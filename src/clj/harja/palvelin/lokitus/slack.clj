(ns harja.palvelin.lokitus.slack
  "Logger, joka lähettää Slack kanavalle viestit"
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]
            [cheshire.core :as cheshire]))

(def kone (.getHostName (java.net.InetAddress/getLocalHost)))

(defn- rivin-vaihto
  [teksti]
  (str/replace teksti #"\|\|\|" "\n"))


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
                              (mapv #(update % :value rivin-vaihto)
                                    (:fields msg))
                              [{:title (str/upper-case (name level))
                                :value msg}])
                    ;; Slackin attachmentin kentat eli fieldsit ymmärtää slackin markdownia (*, ```, jne.) oikein, kun tämä asetetaan.
                    :mrkdwn_in ["fields"]}
        tekstikentta {:text (rivin-vaihto (:tekstikentta msg))}
        liitteet {:username kone
                :attachments
                [(if (map? msg)
                   (assoc attachment :text (:text msg))
                   attachment)]}
        viesti (merge liitteet tekstikentta)]
    (http/post
     webhook-url
     {:headers {"Content-Type" "application/json"}
      :body (cheshire/encode
             viesti)})))

(defn stack-trace-element [ste]
  (str (.getClassName ste) "." (.getMethodName ste) " "
       "(" (.getFileName ste) ":" (.getLineNumber ste) ")"))

(defn exception-fields [e]
  [{:title "Exception" :value (.getName (.getClass e))}
   {:title "Message" :value (.getMessage e)}
   {:title "Stacktrace"
    :value (str/join
            "\n"
            (map stack-trace-element
                 (take 5 (.getStackTrace e)))) }])

(defn- slack-log-fn [webhook-url {:keys [level vargs ?err output_ msg_] :as argmap}]
  (let [msg (if (map? (first vargs))
              (first vargs)
              {:fields [{:title (str/upper-case (name level))
                         :value (force msg_)}]})]
    (laheta webhook-url level
            (if ?err
              (update msg :fields into (exception-fields ?err))
              msg))))

(defn luo-slack-appender [webhook-url taso]
  {:doc "Slack appender"
   :min-level taso
   :enabled? true
   :async? true
   :limit-per-msecs nil
   :fn (fn [data]
         (slack-log-fn webhook-url data))})
