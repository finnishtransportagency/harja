(ns harja.palvelin.palvelut.integraatioloki
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.integraatioloki :as q]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.roolit :as roolit]))

(declare hae-jarjestelmien-integraatiot)
(declare hae-integraatiotapahtumat)
(declare hae-integraatiotapahtuman-viestit)

(defrecord Integraatioloki []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-jarjestelmien-integraatiot
                      (fn [kayttaja _]
                        (hae-jarjestelmien-integraatiot (:db this) kayttaja)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-integraatiotapahtumat
                      (fn [kayttaja {:keys [jarjestelma integraatio alkaen paattyen]}]
                        (hae-integraatiotapahtumat (:db this) kayttaja jarjestelma integraatio alkaen paattyen)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-integraatiotapahtuman-viestit
                      (fn [kayttaja tapahtuma-id]
                        (hae-integraatiotapahtuman-viestit (:db this) kayttaja tapahtuma-id)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-jarjestelmien-integraatiot)
    (poista-palvelu (:http-palvelin this) :hae-integraatiotapahtumat)
    (poista-palvelu (:http-palvelin this) :hae-integraatiotapahtuman-viestit)
    this))

(defn muunna-merkkijono-kartaksi [merkkijono]
  ;; todo: read-stringing käyttö voi olla turvatonta. kysy tatulta parempi tapa.
  (if (and merkkijono (not (empty? merkkijono)))
    (let [kartta (binding [*read-eval* false] (read-string merkkijono))]
      kartta)
    nil))

(def viesti-xf
  (comp
    (map #(assoc % :parametrit (muunna-merkkijono-kartaksi (:parametrit %))))
    (map #(assoc % :otsikko (muunna-merkkijono-kartaksi (:otsikko %))))))

(def tapahtuma-xf
  (comp
   (map konversio/alaviiva->rakenne)
   (map #(assoc % :onnistunut (boolean (Boolean/valueOf (:onnistunut %)))))))

(defn hae-integraatiot [db]
  (let [integraatiot (q/hae-jarjestelmien-integraatiot db)
        uniikit-integraatiot (mapv (fn [kartta]
                                     (assoc kartta :integraatiot
                                            (mapv #(:integraatio %)
                                                  (into []
                                                        (filter #(= (:jarjestelma %) (:jarjestelma kartta))) integraatiot))))
                                   (set (map #(dissoc % :integraatio) integraatiot)))]
    (log/debug "Integraatiot:" uniikit-integraatiot)
    uniikit-integraatiot))

(defn hae-jarjestelmien-integraatiot
  "Palvelu, joka palauttaa kaikki eri järjestelmien integraatiot."
  [db kayttaja]
  (roolit/vaadi-rooli kayttaja roolit/jarjestelmavastuuhenkilo)
  (log/debug "Haetaan järjestelmien integraatiot.")
  (hae-integraatiot db))

(defn hae-integraatiotapahtumat
  "Palvelu, joka palauttaa järjestelmän integraation tapahtumat tietyltä aikaväliltä."
  [db kayttaja jarjestelma integraatio alkaen paattyen]
  (roolit/vaadi-rooli kayttaja roolit/jarjestelmavastuuhenkilo)
  (log/debug "Haetaan tapahtumat järjestelmän:" jarjestelma ", integraatiolle:" integraatio ", alkaen:" alkaen ", paattyen:" paattyen)
  (let [tapahtumat
        (into []
              tapahtuma-xf
              (q/hae-jarjestelman-integraatiotapahtumat-aikavalilla db
                                                                    (if jarjestelma true false) jarjestelma
                                                                    (if integraatio true false) integraatio
                                                                    (konversio/sql-date alkaen)
                                                                    (konversio/sql-date paattyen)))]
    (log/debug "Tapahtumat:" tapahtumat)
    tapahtumat
    #_(let [tapahtumat-viesteineen
          (mapv #(assoc % :viestit
                        (into []
                              viesti-xf
                              (q/hae-integraatiotapahtuman-viestit db (:id %)))) tapahtumat)]
      (log/debug "Tapahtumat viesteineen:" tapahtumat-viesteineen)
      tapahtumat-viesteineen)))

(defn hae-integraatiotapahtuman-viestit [db kayttaja tapahtuma-id]
  (roolit/vaadi-rooli kayttaja roolit/jarjestelmavastuuhenkilo)
  (into []
        viesti-xf
        (q/hae-integraatiotapahtuman-viestit db tapahtuma-id)))


