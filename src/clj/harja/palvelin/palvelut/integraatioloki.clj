(ns harja.palvelin.palvelut.integraatioloki
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.integraatioloki :as q]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.string :as str]
            [clj-time.core :as t]))


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
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
  (log/debug "Haetaan järjestelmien integraatiot.")
  (hae-integraatiot db))

(defn hae-integraatiotapahtumat
  "Palvelu, joka palauttaa järjestelmän integraation tapahtumat tietyltä aikaväliltä."
  [db kayttaja jarjestelma integraatio alkaen paattyen hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
  (let [{:keys [otsikot parametrit viestin-sisalto tapahtumien-tila tapahtumien-kesto max-tulokset]} hakuehdot
        otsikot (if (str/blank? otsikot) nil otsikot)
        parametrit (if (str/blank? parametrit) nil parametrit)
        viestin-sisalto (if (str/blank? viestin-sisalto) nil viestin-sisalto)
        onnistuneet (case tapahtumien-tila
                      :onnistuneet true
                      :epaonnistuneet false
                      nil)
        limit 500
        tapahtumien-kesto (when tapahtumien-kesto
                            (/ (t/in-millis (t/minutes tapahtumien-kesto)) 1000))
        tapahtumat (into []
                         tapahtuma-xf
                         (q/hae-jarjestelman-integraatiotapahtumat-aikavalilla db
                                                                               jarjestelma
                                                                               integraatio
                                                                               onnistuneet
                                                                               (konversio/sql-date alkaen)
                                                                               (konversio/sql-date paattyen)
                                                                               otsikot
                                                                               parametrit
                                                                               viestin-sisalto
                                                                               tapahtumien-kesto
                                                                               limit))]
    tapahtumat))

(defn hae-integraatiotapahtumien-maarat
  [db kayttaja jarjestelma integraatio]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
  (let [
        jarjestelma (when jarjestelma (:jarjestelma jarjestelma))
        maarat (q/hae-integraatiotapahtumien-maarat
                 db
                 (boolean jarjestelma) jarjestelma
                 (boolean integraatio) integraatio)]
    maarat))

(defn hae-integraatiotapahtuman-viestit [db kayttaja tapahtuma-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
  (into []
        viesti-xf
        (q/hae-integraatiotapahtuman-viestit db tapahtuma-id)))



(defrecord Integraatioloki []
  component/Lifecycle
  (start [this]
    (let [db (:db-replica this)
          http-palvelin (:http-palvelin this)]
      (julkaise-palvelu http-palvelin
                        :hae-jarjestelmien-integraatiot
                        (fn [kayttaja _]
                          (hae-jarjestelmien-integraatiot db kayttaja)))
      (julkaise-palvelu http-palvelin
                        :hae-integraatiotapahtumat
                        (fn [kayttaja {:keys [jarjestelma integraatio alkaen paattyen hakuehdot]}]
                          (hae-integraatiotapahtumat db kayttaja jarjestelma integraatio alkaen paattyen hakuehdot)))
      (julkaise-palvelu http-palvelin
                        :hae-integraatiotapahtumien-maarat
                        (fn [kayttaja {:keys [jarjestelma integraatio]}]
                          (hae-integraatiotapahtumien-maarat db kayttaja jarjestelma integraatio)))
      (julkaise-palvelu http-palvelin
                        :hae-integraatiotapahtuman-viestit
                        (fn [kayttaja tapahtuma-id]
                          (hae-integraatiotapahtuman-viestit db kayttaja tapahtuma-id))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-jarjestelmien-integraatiot)
    (poista-palvelu (:http-palvelin this) :hae-integraatiotapahtumat)
    (poista-palvelu (:http-palvelin this) :hae-integraatiotapahtumien-maarat)
    (poista-palvelu (:http-palvelin this) :hae-integraatiotapahtuman-viestit)
    this))
