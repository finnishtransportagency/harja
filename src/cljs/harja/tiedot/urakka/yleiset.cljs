(ns harja.tiedot.urakka.yleiset
  "Tämä nimiavaruus hallinnoi urakan yleiset-sivun tietoja."
  (:require [reagent.core :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.domain.urakka :as urakka]
            [harja.domain.vesivaylat.alus :as alus]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tyokalut.local-storage :as local-storage]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce aikajana
  (local-storage/local-storage-atom :paivystajat-aikajana
                                    {:nayta-aikajana? false
                                     :vain-urakoitsijat? false} nil))

(defonce nayta-aikajana? (r/cursor aikajana [:nayta-aikajana?]))
(defonce aikajana-vain-urakoitsijat? (r/cursor aikajana [:vain-urakoitsijat?]))

(defn toggle-nayta-aikajana! []
  (swap! nayta-aikajana? not))

(defn toggle-aikajana-vain-urakoitsijat! []
  (swap! aikajana-vain-urakoitsijat? not))

(def yhteyshenkilotyypit-kaikille-urakoille
  (into [] (sort ["Kunnossapitopäällikkö" "Tieliikennekeskus"
                  "Viranomais- ja vahingonkorvausasiat" "Työmaavalvoja" "Työmaavalvojan sijainen"])))

(def yhteyshenkilotyypit-vesivaylat
  (into [] (sort ["Sopimusvastaava"])))

(def yhteyshenkilotyypit-oletus
  (into [] (sort (concat yhteyshenkilotyypit-kaikille-urakoille
                         ["Sillanvalvoja" "Kelikeskus"]))))

(def yhteyshenkilotyypit-paallystys
  (into [] (sort (concat yhteyshenkilotyypit-kaikille-urakoille
                         ["Aluevastaava" "Tiemerkintäurakan tilaaja" "Siltainsinööri"
                          "Turvallisuuskoordinaattori"]))))

(def yhteyshenkilotyypit-tiemerkinta
  (into [] (sort (concat yhteyshenkilotyypit-kaikille-urakoille
                         ["Aluevastaava" "Päällystysurakan tilaaja"
                          "Turvallisuuskoordinaattori"]))))

(defn urakkatyypin-mukaiset-yhteyshenkilotyypit [urakkatyyppi]
  (cond
    (urakka/vesivaylaurakkatyyppi? urakkatyyppi) yhteyshenkilotyypit-vesivaylat
    (= :paallystys urakkatyyppi) yhteyshenkilotyypit-paallystys
    (= :tiemerkinta urakkatyyppi) yhteyshenkilotyypit-tiemerkinta
    :default yhteyshenkilotyypit-oletus))

(defn tallenna-urakan-yhteyshenkilot
  "Tallentaa urakan yhteyshenkilöt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id yhteyshenkilot poistettavat]
  ;;(log "TALLENNA URAKAN YHTEYSHENKILOT: " (pr-str yhteyshenkilot) " \n JA POISTETAAN: " (pr-str poistettavat))
  (log " YHTEYSHENKILOT: " yhteyshenkilot)
  (k/post! :tallenna-urakan-yhteyshenkilot
           {:urakka-id urakka-id
            :yhteyshenkilot yhteyshenkilot
            :poistettu poistettavat}))

(defn hae-urakan-kayttajat [urakka-id]
  (k/post! :hae-urakan-kayttajat urakka-id nil true))

(defn hae-urakan-vastuuhenkilot [urakka-id]
  (k/post! :hae-urakan-vastuuhenkilot urakka-id))

(defn hae-urakan-paivystajat [urakka-id]
  (k/post! :hae-urakan-paivystajat urakka-id))

(defn hae-urakan-yhteyshenkilot [urakka-id]
  (k/post! :hae-urakan-yhteyshenkilot urakka-id))

(defn tallenna-urakan-alukset [urakka-id urakoitsija-id alukset tulos-atom]
  (go (let [vastaus (<! (k/post! :tallenna-urakoitsijan-alukset {::urakka/id urakka-id
                                                                 ::alus/urakoitsija-id urakoitsija-id
                                                                 ::alus/tallennettavat-alukset alukset}))]
        (if (k/virhe? vastaus)
          (viesti/nayta! "Virhe tallennettaessa aluksia" :danger)
          (reset! tulos-atom vastaus)))))

(defn hae-urakoitsijan-alukset [urakka-id urakoitsija-id]
  (k/post! :hae-urakoitsijan-alukset {::urakka/id urakka-id
                                      ::alus/urakoitsija-id urakoitsija-id}))

(defn tallenna-urakan-paivystajat
  "Tallentaa urakan päivystäjät. Palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id paivystajat poistettavat]
  (k/post! :tallenna-urakan-paivystajat
           {:urakka-id urakka-id
            :paivystajat paivystajat
            :poistettu poistettavat}))

(defn tallenna-urakan-vastuuhenkilot-roolille [urakka-id rooli vastuuhenkilo varahenkilo]
  (k/post! :tallenna-urakan-vastuuhenkilot-roolille
           {:urakka-id urakka-id
            :rooli rooli
            :vastuuhenkilo vastuuhenkilo
            :varahenkilo varahenkilo}))
