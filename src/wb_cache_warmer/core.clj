(ns wb-cache-warmer.core
  (:gen-class)
  (:require [clj-http.client :as http]
            [datomic.api :as d]
            [durable-queue :as dq]
            [environ.core :refer [env]]
            [mount.core :as mount]
            [clojure.tools.cli :refer [parse-opts]]
            [taoensso.timbre :refer [info debug warn error]]))


(def ^{:private true} q (dq/queues "/tmp/cache_warmer_queue" {}))


(defn scheduler-put! [job & args]
  (apply dq/put! q :cache_warmer_queue job args))

(defn- scheduler-take! [& args] (apply dq/take! q :cache_warmer_queue args))

(defn- scheduler-complete! [& args] (apply dq/complete! args))

(defn- scheduler-retry! [& args] (apply dq/retry! args))

(defn- scheduler-stats []
  (get (dq/stats q) "cache_warmer_queue"))

(defn job-creator-fun [hostname path-pattern]
  (fn [id]
    (let [path (clojure.string/replace path-pattern #"\{id\}" id)
          url (format "http://%s%s?content-type=application/json" hostname path)]
      {:url url})))

(defn schedule-job [job-creator id]
  (scheduler-put! (job-creator id)))

(defn get-ids-by-type
  "get all WBID of a given type"
  [db type]
  (let [db-type (clojure.string/replace type #"_" "-")
        ident-attr (keyword (format "%s/id" db-type))]
    (d/q '[:find [?id ...]
           :in $ ?ident-attr
           :where [_ ?ident-attr ?id]]
         db
         ident-attr)))

(def slow-path-patterns
  [
   "/rest/widget/gene_class/{id}/current_genes"
   "/rest/widget/gene_class/{id}/previous_genes"
   "/rest/widget/molecule/{id}/affected"
   "/rest/widget/phenotype/{id}/rnai"
   "/rest/widget/phenotype/{id}/variation"
   "/rest/widget/strain/{id}/contains"
   "/rest/widget/transposon_family/{id}/var_motifs"

   "/rest/widget/gene/{id}/interactions"
   "/rest/field/gene/{id}/interaction_details"

   "/rest/widget/wbprocess/{id}/interactions"
   "/rest/field/wbprocess/{id}/interaction_details"

   "/rest/widget/interaction/{id}/interactions"
   "/rest/field/interaction/{id}/interaction_details"
   ])

(defn schedule-jobs-all [db hostname]
  ;; schedule something for testing
  (let [id-fun (fn [path-pattern]
                 (let [[_ _ _ type _ widget] (clojure.string/split path-pattern #"\/")]
                   (cond
                    (and (= type "gene")
                         (or (= widget "interactions")
                             (= widget "interaction_details")))
                    (d/q '[:find [?eid ...]
                           :in $
                           :where
                           [_ :interaction.interactor-overlapping-gene/gene ?g]
                           [?g :gene/id ?eid]]
                         db)
                    :else (get-ids-by-type db type))))]
    (do
      (info "Identifying all endpoints matching pattuern. May take a few minutes...")
      (->> slow-path-patterns
           (mapcat (fn [path-pattern]
                     (do
                       (info "Identifying endpoints with pattern" path-pattern)
                       (let [ids (id-fun path-pattern)
                             job-creator (job-creator-fun hostname path-pattern)]
                         (map schedule-job (repeat job-creator) ids)))))
           (doall))))
)

(defn schedule-jobs-sample [db hostname]
  (let [job-creator (job-creator-fun hostname "/rest/widget/gene/{id}/interactions")]
    (->> ["WBGene00015146"
          "WBGene00000904"
          "WBGene00006763"
          "WBGene00002285"
          "WBGene00003912"
          "WBGene00004357"
          "WBGene00000103"]
         (map schedule-job (repeat job-creator))
         (doall)))
  )


(defn execute-job [job]
  (http/get (:url job)))


(defn worker [db]
  (future
    (loop []
      (debug "Stats" (scheduler-stats))
      (if-let [job-ref (scheduler-take! 10000 nil)]
        ;; normal batches won't be nil
        ;; only get nil when no more jobs are added to the queue for a period of time
        (do
          (try
            (let [job (deref job-ref)]
              (try
                (debug "Starting" job)
                (execute-job job)
                (debug "Succeeded" job)
                (scheduler-complete! job-ref)

                (catch Exception e
                  (error e)
                  (warn "Failed and scheduled to retry" job)
                  (scheduler-retry! job-ref) ; retried items are added at the end of the queue
                  )))
            (catch java.io.IOException e
              (error "Corrupted reference" job-ref)
              (scheduler-complete! job-ref)))
          (recur))

        )))
  )


(defn- connect []
  (d/connect (env :wb-db-uri)))

(defn- disconnect [conn]
  (d/release conn))

(mount/defstate datomic-conn
  :start (connect)
  :stop (disconnect datomic-conn))


(def cli-options
  [["-H" "--hostname HOSTNAME" "Host to cache from"
    :default "wormbase-website-preproduction.us-east-1.elasticbeanstalk.com"]
   ["-n" "--thread-count N" "Thread counts"
    :default 5
    :parse-fn #(Integer/parseInt %)]
   ["-a" "--schedule-all" "Cache all endpoints that is considered slow"
    :default false]
   [nil "--schedule-sample" "Cache a preset sample of endpoints"
    :default false]
   ["-h" "--help"]
   ])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
     (:help options)
     (println summary)
     :else
     (do
       (info "CLI Options" options errors)
       (mount/start)
       (let [db (d/db datomic-conn)
             hostname (:hostname options)
             n (:thread-count options)]
         (do
           (cond
            (:schedule-all options) (schedule-jobs-all db hostname)
            (:schedule-sample options) (schedule-jobs-sample db hostname))

           (info "Start caching...")
           (->> (partial worker db)
                (repeatedly n)
                (pmap deref) ; wait for the futures to return
                (doall) ; force the side effects
                )

           (info "Stopping!" (scheduler-stats))

           ))

       ))))
