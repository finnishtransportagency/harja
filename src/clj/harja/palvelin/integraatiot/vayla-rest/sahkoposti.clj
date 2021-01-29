(ns harja.palvelin.integraatiot.vayla-rest.sahkoposti
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.kyselyt.integraatiot :as q]
            [harja.palvelin.integraatiot.sahkoposti :refer [Sahkoposti]]
            [harja.palvelin.tapahtuma-protokollat :refer [Kuuntele]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log])
  (:import (java.util UUID)))

;; mietittävää: rest-lähetysrajapinnasta tulossa synkroninen, eli kutsu voi kestää - ok blokata jutsuja siksi aikaa vai tehdäänkö esim futurella?


(defn lokittaja [{il :integraatioloki db :db} nimi]
  (integraatioloki/lokittaja il db "vayla-rest" nimi))

(defrecord VaylaRestSahkoposti [vastausosoite jonot kuuntelijat kuittaus-kuuntelijat]
  component/Lifecycle
  (start [{vayla-rest :vayla-rest :as this}]
    (log/debug "VaylaRestSahkoposti-komponentti käynnistyy"))

  (stop [this]
    (reset! kuuntelijat #{})
    (reset! kuittaus-kuuntelijat {}))

  Sahkoposti
  (rekisteroi-kuuntelija! [this kuuntelija-fn]
    ;; -> todo: vastaanotamme sähköpostitse ihmisten lähettämiä sähköpostiviestejä, joilla kuitataan juttuja meille.
    ;;   eli sonja-komponentin 2 kuittauskujuntelijaa ovat lähetyksen kuittauskuuntelija, jossa kuitataan että viesti on lähetettyj ja toisekseen sähköpostin vastaanottaja jota esim tloik käyttää vastsaanottamaan ihmisen lähettämiä ilmoitusten kuittauksia
    ;; (ks funktio harja.palvelin.integraatiot.tloik.sahkoposti/vastaanota-sahkopostikuittaus).
    ;; -> tehdään api endpoint harjaan tälle.
    (swap! kuuntelijat conj kuuntelija-fn)
    #(swap! kuuntelijat disj kuuntelija-fn))

  (laheta-viesti! [{jms-lahettaja :jms-lahettaja} lahettaja vastaanottaja otsikko sisalto]
    (let [viesti-id (str (UUID/randomUUID))
          sahkoposti (sanomat/sahkoposti viesti-id lahettaja vastaanottaja otsikko sisalto)
          viesti (xml/tee-xml-sanoma sahkoposti)]
      {:jms-message-id (jms-lahettaja viesti viesti-id)
       :viesti-id viesti-id}))

  (laheta-viesti-ja-liite! [{jms-lahettaja :jms-lahettaja-sahkoposti-ja-liite} lahettaja vastaanottajat otsikko sisalto tiedosto-nimi]
    (let [viesti-id (str (UUID/randomUUID))
          sahkoposti (sanomat/sahkoposti-ja-liite viesti-id vastaanottajat lahettaja otsikko (:viesti sisalto) tiedosto-nimi (pvm/nyt))
          viesti (xml/tee-xml-sanoma sahkoposti)]
      {:jms-message-id (jms-lahettaja {:xml-viesti viesti :pdf-liite (:pdf-liite sisalto)} viesti-id)
       :viesti-id viesti-id}))

  (vastausosoite [this]
    vastausosoite)

  Kuuntele
  (kuuntele! [this jono kuuntelija-fn]
    (swap! kuittaus-kuuntelijat update jono (fn [rekisteroidyt-kuuntelijat]
                                              (if (nil? rekisteroidyt-kuuntelijat)
                                                #{kuuntelija-fn}
                                                (conj rekisteroidyt-kuuntelijat kuuntelija-fn))))
    #(swap! kuittaus-kuuntelijat update jono disj kuuntelija-fn)))

(defn luo-sahkoposti [vastausosoite jonot]
  (->VaylaRestSahkoposti vastausosoite jonot (atom #{}) (atom {})))
