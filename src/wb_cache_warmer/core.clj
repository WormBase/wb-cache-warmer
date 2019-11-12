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

(defn schedule-job [url]
  (scheduler-put! {:url url}))

(defn schedule-jobs [hostname path-pattern id-fun]
  (let [ids (id-fun)
        format-url (fn [id]
                     (let [path (clojure.string/replace path-pattern #"\{id\}" id)]
                       (format "http://%s%s" hostname path)))]
    (->>
     ids
     (map format-url)
     (map schedule-job)
     (doall))))

(defn get-eids-by-type
  "get all datomic entity ids of a given type
  indicated by its unique attribute ident
  such as :gene/id"
  [db ident-attr]
  (d/q '[:find [?eid ...]
         :in $ ?ident-attr
         :where [?eid ?ident-attr]]
       db ident-attr))

(defn schedule-jobs-all [db hostname]
  ;; schedule something for testing
)

(defn schedule-jobs-sample [db hostname]
  (schedule-jobs hostname
                 "/rest/widget/gene/{id}/interactions"
                 (fn []
                   ["WBGene00015146",
                    "WBGene00000904",
                    "WBGene00006763",
                    "WBGene00002285"
                    "WBGene00003912"
                    "WBGene00004357"
                    "WBGene00000103"]))
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
    :default "wormbase-website-production.us-east-1.elasticbeanstalk.com"
    ]
   ["-a" "--schedule-all" "Cache all endpoints that is considered slow"
    :default false]
   [nil "--schedule-sample" "Cache a preset sample of endpoints"
    :default false]
   ])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (do
      (info "Options" options)
      (mount/start)
      (let [db (d/db datomic-conn)
            hostname (:hostname options)]
        (do
          (cond
           (:schedule-all options) (schedule-jobs-all db hostname)
           (:schedule-sample options) (schedule-jobs-sample db hostname))

          (->> (partial worker db)
               (repeatedly 5)
               (pmap deref) ; wait for the futures to return
               (doall) ; force the side effects
               )

          (info "Stopping!" (scheduler-stats))

          ))

      )))
