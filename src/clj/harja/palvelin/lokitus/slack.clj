(ns harja.palvelin.lokitus.slack
  "Logger, joka lähettää Slack kanavalle viestit"
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]
            [cheshire.core :as cheshire]))

(def kone (.getHostName (java.net.InetAddress/getLocalHost)))


(defn laheta [webhook-url level msg]
  (let [attachment {:fallback ""

                    ;; Käytetään slackin esimääriteltyjä värejä levelin mukaan
                    :color (case level
                             :warn "warning"
                             :error "danger"
                             "good")

                    ;; Näytetään viesti kenttänä, jonka otsikkona on taso
                    ;; ja arvona virheviesti
                    :fields (if (map? msg)
                              (mapv #(assoc % :value (str/replace (:value %) #"\(slack-n\)" "\n")) (:fields msg))
                              [{:title (str/upper-case (name level))
                                :value msg}])}]
    (println (pr-str "ATTACHMENT: " attachment))
    (http/post
     webhook-url
     {:headers {"Content-Type" "application/json"}
      :body (cheshire/encode
             {:username kone
              :attachments
              [(if (map? msg)
                  (assoc attachment :text (:text msg))
                  attachment)]})})))

(defn luo-slack-appender [webhook-url taso]
  {:doc "Slack appender"
   :min-level taso
   :enabled? true
   :async? true
   :limit-per-msecs nil
   :fn (fn [{:keys [ap-config config level prefix throwable message args] :as args}]
         (let [[slack-arg] args
               msg (if (map? slack-arg)
                     slack-arg
                     message)]
           (laheta webhook-url level msg)))})
