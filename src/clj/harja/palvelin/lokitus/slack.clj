(ns harja.palvelin.lokitus.slack
  "Logger, joka lähettää Slack kanavalle viestit"
  (:require [org.httpkit.client :as http]
            [clojure.string :as clj-str]
            [clojure.edn :as clj-edn]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm])
  (:import [java.text SimpleDateFormat]
           [java.util Date]))

(def kone (.getHostName (java.net.InetAddress/getLocalHost)))

(defn- merkkien-vaihto-slackiin
  "Vaihdetaan Slackiin menevät merkit oikeaan muotoonsa. Tämä ominaismerkit ovat käytössä, koska
   muihin logituspaikkoihin menevät viestit ei näitä tarvi tai koko logitusviestiä on muutettu jo
   ennalta jollain tavalla, esim. kaikki rivinvaihdot otetaan pois ennen kuin viesti tulee tähän ns asti."
  [{:keys [ilog glog jira]} spesifit-tiedot-poistettu teksti]
  (when teksti
    (let [[j-ensimmainen j-toinen] jira
          {ilog-url :url tapahtuma-id-par :tapahtuma-id
           alkanut-par :alkanut valittu-jarjestelma-par :valittu-jarjestelma
           valittu-integraatio-par :valittu-integraatio} ilog
          {glog-url :url from-par :from to-par :to q-par :q} glog
          poista-ja-muokkaa-ei-sallitut-merkit (fn [contains-text-kentta]
                                                 (clj-str/replace (apply str
                                                                         (re-seq #"[0-9a-zA-ZäÄöÖåÅ_-]|:" contains-text-kentta))
                                                                  #":" "%3A"))
          merkkien-parsiminen (fn [teksti regex-aloitus regex-lopetus funktio]
                                (apply str
                                       (mapcat (fn [tekstin-loput]
                                                 (let [tekstiosat (clj-str/split tekstin-loput regex-lopetus)]
                                                   (if (or (> (count tekstiosat) 1)
                                                           (and (not-empty tekstin-loput)
                                                                (< (count (first tekstiosat)) (count tekstin-loput))))
                                                     (funktio tekstiosat)
                                                     tekstiosat)))
                                               (clj-str/split teksti regex-aloitus))))
          muodosta-kysely-url (fn [url parametrit argumentit]
                                (apply str
                                       url
                                       (interpose "&"
                                                  (map #(apply str %)
                                                       (partition 2
                                                                  (interleave
                                                                    parametrit
                                                                    argumentit))))))
          ilog-linkit-muodostettu (if (= teksti :oletus-linkki)
                                    (str "<|||jirajira||||JIRA> | "
                                         "<|||glogglog||||Graylog>")
                                    (merkkien-parsiminen teksti
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
                                                             (apply str (muodosta-kysely-url ilog-url
                                                                                             [tapahtuma-id-par alkanut-par valittu-jarjestelma-par valittu-integraatio-par]
                                                                                             [tapahtuma-id-arg alkanut-arg valittu-jarjestelma-arg valittu-integraatio-arg])
                                                                    (rest tekstiosat))))))
          glog-linkit-muodostettu (merkkien-parsiminen ilog-linkit-muodostettu
                                                       #"\|\|\|glog"
                                                       #"glog\|\|\|"
                                                       (fn [tekstiosat]
                                                         ;; Jos ilog||| merkkijonoja on enemmän kuin yksi tällä pätkällä, ei käsitellä muita kuin ensimmäinen
                                                         (when (> 2 (count tekstiosat))
                                                           (log/debug "Linkkien lokituksessa slackiin virhe: glog||| tageja enemmän kuin yksi peräkkäin."))
                                                         (let [{from-arg :from to-arg :to q-arg :q}
                                                               (try (clj-edn/read-string (first tekstiosat))
                                                                    (catch Exception e
                                                                      (log/debug "Virhe glog mapin parsimisessa: " e)
                                                                      nil))
                                                               aikavali (pvm/aikavali-nyt-miinus 1)
                                                               from-arg (or from-arg (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") (.toDate (first aikavali))))
                                                               to-arg (or to-arg (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") (.toDate (last aikavali))))
                                                               q-arg (or q-arg (apply str
                                                                                      (interpose "%20OR%20" (keep (fn [fieldin-tekstit]
                                                                                                                    (when-not (empty? fieldin-tekstit)
                                                                                                                      (apply str
                                                                                                                             (interpose "%20AND%20"
                                                                                                                                        (map #(str "%22" % "%22")
                                                                                                                                             fieldin-tekstit)))))
                                                                                                                 spesifit-tiedot-poistettu))))]
                                                           (apply str (muodosta-kysely-url glog-url
                                                                                           [from-par to-par q-par]
                                                                                           [from-arg to-arg q-arg])
                                                                  (rest tekstiosat)))))
          jira-linkit-muodostettu (merkkien-parsiminen glog-linkit-muodostettu
                                                       #"\|\|\|jira"
                                                       #"jira\|\|\|"
                                                       (fn [tekstiosat]
                                                         (let [vapaa-teksti (poista-ja-muokkaa-ei-sallitut-merkit
                                                                              (if-not (empty? (first tekstiosat))
                                                                                (first tekstiosat)
                                                                                (str (hash spesifit-tiedot-poistettu))))]
                                                           ;; Jos jira||| merkkijonoja on enemmän kuin yksi tällä pätkällä, ei käsitellä muita kuin ensimmäinen
                                                           (when (> 2 (count tekstiosat))
                                                             (log/debug "Linkkien lokituksessa slackiin virhe: jira||| tageja enemmän kuin yksi peräkkäin."))
                                                           (apply str
                                                                  j-ensimmainen
                                                                  vapaa-teksti
                                                                  j-toinen " "
                                                                  (rest tekstiosat)))))
          korvattavat-tekstit (-> jira-linkit-muodostettu
                                  (clj-str/replace #"\|\|\|" "\n"))]
      korvattavat-tekstit)))


(defn laheta [webhook-url level msg urls]
  (let [muuta-hx (fn [teksti-seq]
                   (sequence (comp
                               (filter #(not (empty? %)))
                               (map konv/str->hx))
                             teksti-seq))
        spesifit-tiedot-poistettu (sequence (comp
                                              (filter #(and (not= (:title %) "Stacktrace")
                                                            (string? (:value %))))
                                              (map :value)
                                              (map #(clj-str/replace % #"[\n\r]" ""))
                                              (keep #(let [muutettu-teksti (muuta-hx (clj-str/split % #"\d+|\{.*\}"))]
                                                       (when-not (empty? muutettu-teksti)
                                                         muutettu-teksti))))
                                            (:fields msg))
        attachment {:fallback ""

                    ;; Käytetään slackin esimääriteltyjä värejä levelin mukaan
                    :color (case level
                             :warn "warning"
                             :error "danger"
                             "good")

                    ;; Näytetään viesti kenttänä, jonka otsikkona on taso
                    ;; ja arvona virheviesti
                    :fields (if (map? msg)
                              (mapv #(update % :value (partial merkkien-vaihto-slackiin urls spesifit-tiedot-poistettu))
                                    (:fields msg))
                              [{:title (clj-str/upper-case (name level))
                                :value msg}])
                    ;; Slackin attachmentin kentat eli fieldsit ymmärtää slackin markdownia (*, ```, jne.) oikein, kun tämä asetetaan.
                    :mrkdwn_in ["fields"]}
        tekstikentta {:text (merkkien-vaihto-slackiin urls spesifit-tiedot-poistettu (:tekstikentta msg))}
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
                         :value (force msg_)}
                        {:title "Linkit"
                         :value :oletus-linkki}]})]
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
