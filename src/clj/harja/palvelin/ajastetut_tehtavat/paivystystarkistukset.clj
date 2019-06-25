(ns harja.palvelin.ajastetut-tehtavat.paivystystarkistukset
  (:require [taoensso.timbre :as log]
            [chime :refer [chime-ch]]
            [chime :refer [chime-at]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot-q]
            [harja.palvelin.palvelut.urakat :as urakat]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [clj-time.core :as t]
            [harja.kyselyt.konversio :as konv]
            [clj-time.coerce :as c]
            [harja.tyokalut.html :refer [sanitoi]]
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
          (sanitoi urakka-nimi)
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
  (fim/hae-urakan-kayttajat-jotka-roolissa fim sampo-id #{"ely urakanvalvoja" "urakan vastuuhenkilö"}))

(defn- ilmoita-paivystyksettomasta-urakasta [urakka fim email pvm]
  (let [ilmoituksen-saajat (hae-ilmoituksen-saajat fim (:sampoid urakka))]
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
  [paivystykset urakat pvm]
  (log/debug "Tarkistetaan urakkakohtaisesti, onko annetulle päivälle " (pr-str pvm) " olemassa päivystys.")
  ;; PENDING Mahdollisesti olisi hyvä tarkistaa esim. niin että joka tunnille on päivystys.
  ;; Nyt ei tule varoitusta jos päivystys ei täytä koko ajanjaksoa. Mietitään tämä myöhemmin.
  (let [urakalla-paivystys-annettuna-paivana?
        (fn [paivystykset urakka pvm]
          (let [urakan-paivystykset (filter
                                      #(= (:urakka-id %) (:id urakka))
                                      paivystykset)
                paivystys-annettuna-paivana? (fn [pvm paivystys-alku paivystys-loppu]
                                               (pvm/valissa? pvm
                                                             paivystys-alku
                                                             paivystys-loppu
                                                             true))]
            (and (not (empty? urakan-paivystykset))
                 (some?
                   (some #(paivystys-annettuna-paivana?
                           pvm
                           (:paivystys-alku %)
                           (:paivystys-loppu %))
                     urakan-paivystykset)))))]

    (filter
      #(not (urakalla-paivystys-annettuna-paivana? paivystykset % pvm))
      urakat)))

(defn hae-voimassa-olevien-urakoiden-paivystykset
  [db pvm]
  (let [urakoiden-paivystykset (into []
                                     (map konv/alaviiva->rakenne)
                                     (yhteyshenkilot-q/hae-kaynissa-olevien-urakoiden-paivystykset
                                       db
                                       {:pvm (c/to-sql-time pvm)}))
        urakoiden-paivystykset (map
                                 #(-> %
                                      (assoc :paivystys-alku
                                             (pvm/suomen-aikavyohykkeeseen (c/from-sql-time (:paivystys-alku %))))
                                      (assoc :paivystys-loppu
                                             (pvm/suomen-aikavyohykkeeseen (c/from-sql-time (:paivystys-loppu %)))))
                                 urakoiden-paivystykset)]
    urakoiden-paivystykset))

(defn hae-urakat-paivystystarkistukseen
  ;; TODO Poista urakkatyyppi-filtteri kun kaikki urakat tuotannossa (joku kaunis päivä)
  ([db pvm] (hae-urakat-paivystystarkistukseen db pvm nil))
  ([db pvm urakkatyyppi]
  (yhteyshenkilot-q/hae-urakat-paivystystarkistukseen db {:pvm (c/to-sql-time pvm)
                                                          :tyyppi (when urakkatyyppi
                                                                    (name urakkatyyppi))})))

(defn- paivystyksien-tarkistustehtava [db fim email nykyhetki]
  (let [voimassa-olevat-urakat (hae-urakat-paivystystarkistukseen db nykyhetki :hoito)
        paivystykset (hae-voimassa-olevien-urakoiden-paivystykset db nykyhetki)
        urakat-ilman-paivystysta (urakat-ilman-paivystysta paivystykset voimassa-olevat-urakat nykyhetki)]
    (ilmoita-paivystyksettomista-urakoista urakat-ilman-paivystysta fim email nykyhetki)))

(defn tee-paivystyksien-tarkistustehtava
  "Tarkistaa, onko urakalle olemassa päivystys tarkistushetkeä seuraavana päivänä.
   Käsittelee vain ne urakat, jotka ovat voimassa annettuna päivänä."
  [{:keys [db fim sonja-sahkoposti] :as this} paivittainen-aika]
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
