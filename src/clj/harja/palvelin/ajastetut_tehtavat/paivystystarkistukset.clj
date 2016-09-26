(ns harja.palvelin.ajastetut-tehtavat.paivystystarkistukset
  (:require [taoensso.timbre :as log]
            [chime :refer [chime-ch]]
            [chime :refer [chime-at]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot-q]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [clj-time.core :as t]
            [harja.kyselyt.konversio :as konv]
            [clj-time.coerce :as c]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.fmt :as fmt]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]))

(defn viesti-puuttuvasta-paivystyksesta [urakka-nimi pvm]
  (format "Urakalta: %s puuttuu päivystystiedot Harjassa päivämäärälle: %s.\n
           Käy tarkistamassa urakan päivystäjätiedot Harjassa.\n
           Päivystäjätietoja tarvitaan tieliikennekeskusilmoitusten välittämiseen. \n\n
           Ystävällisin terveisin,\n
           Harja-järjestelmä"
          urakka-nimi
          (fmt/pvm (c/to-date pvm))))

(defn- laheta-ilmoitus-henkiloille [email urakka-nimi henkilot pvm]
  (doseq [henkilo henkilot]
    (sahkoposti/laheta-viesti! email
                               (sahkoposti/vastausosoite email)
                               (:sahkoposti henkilo)
                               (format "Harja: Urakalta %s puuttuu päivystystiedot päivämäärälle %s"
                                       urakka-nimi
                                       (fmt/pvm (c/to-date pvm)))
                               (viesti-puuttuvasta-paivystyksesta urakka-nimi pvm))))

(defn hae-ilmoituksen-saajat [fim sampo-id]
  (let [urakan-kayttajat (fim/hae-urakan-kayttajat fim sampo-id)
        ilmoituksen-saajat (filter
                             (fn [kayttaja]
                               (let [roolit (:roolit kayttaja)]
                                 ;; Tarkka match roolit-excelin roolin nimestä
                                 (some #(or (= (str/lower-case %) "ely urakanvalvoja")
                                            (= (str/lower-case %) "urakan vastuuhenkilö"))
                                       roolit)))
                             urakan-kayttajat)]
    ilmoituksen-saajat))

(defn- ilmoita-paivystyksettomasta-urakasta [urakka fim email pvm]
  (let [ilmoituksen-saajat (hae-ilmoituksen-saajat fim (:sampo-id urakka))]
    (if-not (empty? ilmoituksen-saajat)
      (laheta-ilmoitus-henkiloille email (:nimi urakka) ilmoituksen-saajat pvm)
      (log/warn (format "Urakalla %s ei ole päivystystä %s ja urakalle ei löydy FIM:stä henkiöä, jolle tehdä ilmoitus."
                        (:nimi urakka)
                        pvm)))))

(defn- ilmoita-paivystyksettomista-urakoista [urakat-ilman-paivystysta fim email pvm]
  (doseq [urakka urakat-ilman-paivystysta]
    (ilmoita-paivystyksettomasta-urakasta urakka fim email pvm)))

(defn urakat-ilman-paivystysta
  "Palauttaa urakat, joille ei ole päivystystä kyseisenä päivänä"
  [urakoiden-paivystykset pvm]
  (log/debug "Tarkistetaan urakkakohtaisesti, onko annetulle päivälle " (pr-str pvm) " olemassa päivystys.")
  ;; PENDING Mahdollisesti olisi hyvä tarkistaa esim. niin että joka tunnille on päivystys.
  ;; Nyt ei tule varoitusta jos päivystys ei täytä koko ajanjaksoa. Mietitään tämä myöhemmin.
  (let [urakalla-paivystys-annettuna-paivana?
        (fn [paivystykset pvm]
          (let [paivystys-annettuna-paivana? (fn [pvm paivystys-alku paivystys-loppu]
                                               (pvm/valissa? pvm
                                                             paivystys-alku
                                                             paivystys-loppu
                                                             true))]
            (some?
              (some #(paivystys-annettuna-paivana?
                      pvm
                      (:alku %)
                      (:loppu %))
                    paivystykset))))]

    (filter
      #(not (urakalla-paivystys-annettuna-paivana? (:paivystykset %) pvm))
      urakoiden-paivystykset)))

(defn hae-urakoiden-paivystykset
  "Hakee urakoiden päivystystiedot. Palauttaa vain sellaiset urakat jotka ovat olleet
   voimassa annetun päivämäärän aikana."
  [db pvm]
  (let [urakoiden-paivystykset (into []
                                     (map konv/alaviiva->rakenne)
                                     (yhteyshenkilot-q/hae-kaynissa-olevien-urakoiden-paivystykset
                                       db
                                       {:pvm (c/to-sql-time pvm)}))
        urakat (distinct (map #(dissoc % :paivystys) urakoiden-paivystykset))
        ;; sarakkeet-vektoriin ei palauta urakoita, joilla ei ole päivystyksiä.
        ;; Siksi lisätään kaikille urakoille erikseen päivystystiedot
        paivystykset (konv/sarakkeet-vektoriin
                                 urakoiden-paivystykset
                                 {:paivystys :paivystykset}
                                 :id)
        urakoiden-paivystykset (mapv
                                 (fn [urakka]
                                   (if-let [urakan-paivystykset
                                             (first
                                               (filter #(= (:urakka-id %) (:urakka-id urakka))
                                                       paivystykset))]
                                     (assoc urakka :paivystykset (:paivystykset urakan-paivystykset))
                                     urakka))
                                 urakat)
        urakoiden-paivystykset (map (fn [u]
                                      (assoc u :paivystykset
                                               (map #(assoc %
                                                      :alku (c/from-sql-time (:alku %))
                                                      :loppu (c/from-sql-time (:loppu %)))
                                                    (:paivystykset u))))
                                    urakoiden-paivystykset)]
    urakoiden-paivystykset))

(defn- paivystyksien-tarkistustehtava [db fim email nykyhetki]
  (log/info "Päivystystarkistus disabloitu")
  ;; FIXME Disabloitu, koska saattaa lähettää turhia maileja. Täytyy tutkia vika
  ;; Ks. HAR-3139
  #_(let [urakoiden-paivystykset (hae-urakoiden-paivystykset db nykyhetki)
        urakat-ilman-paivystysta (urakat-ilman-paivystysta urakoiden-paivystykset nykyhetki)]
    (ilmoita-paivystyksettomista-urakoista urakat-ilman-paivystysta fim email nykyhetki)))

(defn tee-paivystyksien-tarkistustehtava [{:keys [db fim sonja-sahkoposti] :as this} paivittainen-aika]
  (log/debug "Ajastetaan päivystäjien tarkistus")
  (when paivittainen-aika
    (ajastettu-tehtava/ajasta-paivittain
     paivittainen-aika
     (fn [_]
       (lukot/yrita-ajaa-lukon-kanssa
         db
         "paivystystarkistukset"
         #(paivystyksien-tarkistustehtava db fim sonja-sahkoposti (t/plus (t/now) (t/days 1))))))))

(defrecord Paivystystarkistukset [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this
      :paivystyksien-tarkistus (tee-paivystyksien-tarkistustehtava this (:paivittainen-aika asetukset))))
  (stop [this]
    (doseq [tehtava [:paivystyksien-tarkistus]
            :let [lopeta-fn (get this tehtava)]]
      (when lopeta-fn (lopeta-fn)))
    this))