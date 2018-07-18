(ns harja.palvelin.lokitus.slack
  "Logger, joka lähettää Slack kanavalle viestit"
  (:require [org.httpkit.client :as http]
            [clojure.string :as clj-str]
            [clojure.edn :as clj-edn]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]))

(def kone (.getHostName (java.net.InetAddress/getLocalHost)))

(defn- merkkien-vaihto-slackiin
  "Vaihdetaan Slackiin menevät merkit oikeaan muotoonsa. Tämä ominaismerkit ovat käytössä, koska
   muihin logituspaikkoihin menevät viestit ei näitä tarvi tai koko logitusviestiä on muutettu jo
   ennalta jollain tavalla, esim. kaikki rivinvaihdot otetaan pois ennen kuin viesti tulee tähän ns asti."
  [{:keys [ilog glog jira]} teksti]
  (when teksti
    (let [[j-ensimmainen j-toinen] jira
          {ilog-url :url tapahtuma-id-par :tapahtuma-id
           alkanut-par :alkanut valittu-jarjestelma-par :valittu-jarjestelma
           valittu-integraatio-par :valittu-integraatio} ilog
          poista-ja-muokkaa-ei-sallitut-merkit (fn [contains-text-kentta]
                                                 (->> contains-text-kentta
                                                      (re-seq #"[:0-9a-zA-ZäÄöÖåÅ_-]")
                                                      str
                                                      (clj-str/replace #":" "%3A")))
          merkkien-parsiminen (fn [teksti regex-aloitus regex-lopetus funktio]
                                (apply str
                                       (mapcat (fn [tekstin-loput]
                                                 (let [tekstiosat (clj-str/split tekstin-loput regex-lopetus)]
                                                   (if (> (count tekstiosat) 1)
                                                     (funktio tekstiosat)
                                                     tekstiosat)))
                                               (clj-str/split teksti regex-aloitus))))
          ilog-linkit-muodostettu (merkkien-parsiminen teksti
                                                       #"\|\|\|ilog"
                                                       #"ilog\|\|\|"
                                                       (fn [tekstiosat]
                                                         ;; Jos ilog||| merkkijonoja on enemmän kuin yksi tällä pätkällä, ei käsitellä muita kuin ensimmäinen
                                                         (when (> 2 (count tekstiosat))
                                                           (log/debug "Linkkien lokituksessa slackiin virhe: ilog||| tageja enemmän kuin yksi peräkkäin."))
                                                         (let [{tapahtuma-id-arg :tapahtuma-id
                                                                alkanut-arg :alkanut valittu-jarjestelma-arg :valittu-jarjestelma
                                                                valittu-integraatio-arg :valittu-integraatio}
                                                               (try (clj-edn/read-string (first tekstiosat))
                                                                                    (catch Exception e
                                                                                      (log/debug "Virhe ilog mapin parsimisessa: " e)
                                                                                      nil))]
                                                           (apply str
                                                                  ilog-url
                                                                  (concat
                                                                    (interpose "&"
                                                                               (map #(apply str %)
                                                                                    (partition 2
                                                                                               (interleave
                                                                                                 [tapahtuma-id-par alkanut-par valittu-jarjestelma-par valittu-integraatio-par]
                                                                                                 [tapahtuma-id-arg alkanut-arg valittu-jarjestelma-arg valittu-integraatio-arg]))))
                                                                    (rest tekstiosat))))))
          jira-linkit-muodostettu (merkkien-parsiminen ilog-linkit-muodostettu
                                                       #"\|\|\|jira"
                                                       #"jira\|\|\|"
                                                       (fn [tekstiosat]
                                                         ;; Jos jira||| merkkijonoja on enemmän kuin yksi tällä pätkällä, ei käsitellä muita kuin ensimmäinen
                                                         (when (> 2 (count tekstiosat))
                                                           (log/debug "Linkkien lokituksessa slackiin virhe: jira||| tageja enemmän kuin yksi peräkkäin."))
                                                         (apply str
                                                                j-ensimmainen
                                                                (clj-str/trim (poista-ja-muokkaa-ei-sallitut-merkit (first tekstiosat)))
                                                                j-toinen " "
                                                                (rest tekstiosat))))
          korvattavat-tekstit (-> jira-linkit-muodostettu
                                  (clj-str/replace #"\|\|\|ilog" ilog)
                                  (clj-str/replace #"\|\|\|glog" glog)
                                  (clj-str/replace #"\|\|\|" "\n"))]
      korvattavat-tekstit)))


(defn laheta [webhook-url level msg urls]
  (let [attachment {:fallback ""

                    ;; Käytetään slackin esimääriteltyjä värejä levelin mukaan
                    :color (case level
                             :warn "warning"
                             :error "danger"
                             "good")

                    ;; Näytetään viesti kenttänä, jonka otsikkona on taso
                    ;; ja arvona virheviesti
                    :fields (if (map? msg)
                              (mapv #(update % :value (partial merkkien-vaihto-slackiin urls))
                                    (:fields msg))
                              [{:title (clj-str/upper-case (name level))
                                :value msg}])
                    ;; Slackin attachmentin kentat eli fieldsit ymmärtää slackin markdownia (*, ```, jne.) oikein, kun tämä asetetaan.
                    :mrkdwn_in ["fields"]}
        tekstikentta {:text (merkkien-vaihto-slackiin urls (:tekstikentta msg))}
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
    :value (clj-str/join
             "\n"
             (map stack-trace-element
                  (take 5 (.getStackTrace e))))}])

(defn- slack-log-fn [webhook-url {:keys [level vargs ?err output_ msg_] :as argmap} urls]
  (let [msg (if (map? (first vargs))
              (first vargs)
              {:fields [{:title (clj-str/upper-case (name level))
                         :value (force msg_)}]})]
    (laheta webhook-url level
            (if ?err
              (update msg :fields into (exception-fields ?err))
              msg)
            urls)))

(defn luo-slack-appender [webhook-url taso urls]
  {:doc "Slack appender"
   :min-level taso
   :enabled? true
   :async? true
   :limit-per-msecs nil
   :fn (fn [data]
         (slack-log-fn webhook-url data urls))})
