(ns harja.palvelin.integraatiot.sampo.tyokalut.lokitus
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.kyselyt.integraatioloki :as q]))

(def +poikkeus-samposisaanluvussa+ ::poikkeus-samposisaanluvussa)


(defn tee-lokiviesti [suunta sisalto otsikko]
  {:suunta        suunta
   :sisaltotyyppi "application/xml"
   :siirtotyyppi  "JMS"
   :sisalto       sisalto
   :otsikko       (when otsikko (str otsikko))
   :parametrit    nil})

(defn lokita-viesti [integraatioloki integraatio viesti-id suunta sisalto]
  ;; todo: mieti mitä headeritason tietoja tarvii tallentaa. muista lisätä niille toteutus feikki-sonjaan.
  (let [otsikko {:message-id viesti-id}
        lokiviesti (tee-lokiviesti suunta sisalto otsikko)]
    (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "sampo" integraatio viesti-id lokiviesti)))

(defn lokita-lahteva-kuittaus [integraatioloki kuittaus tapahtuma-id onnistunut lisatietoja]
  (let [lokiviesti (tee-lokiviesti "ulos" kuittaus nil)]
    (if onnistunut
      (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki lokiviesti lisatietoja tapahtuma-id nil)
      (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti lisatietoja tapahtuma-id nil))))

(defn lokita-saapunut-kuittaus [integraatioloki kuittaus ulkoinen-id integraatio onnistunut]
  (log/debug "Kirjataan saapunut kuittaus " kuittaus ", " ulkoinen-id ", " integraatio ", " onnistunut)
  (let [lokiviesti (tee-lokiviesti "sisään" kuittaus nil)]
    (log/debug "LOKIVIESTI: " lokiviesti)
    (if onnistunut
      (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki lokiviesti nil nil ulkoinen-id)
      (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti nil nil ulkoinen-id))))