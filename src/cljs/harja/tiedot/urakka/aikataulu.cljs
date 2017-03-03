(ns harja.tiedot.urakka.aikataulu
  "Ylläpidon urakoiden aikataulu"
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as urakka]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce aikataulu-nakymassa? (atom false))

(defn hae-aikataulu [urakka-id sopimus-id vuosi]
  (k/post! :hae-yllapitourakan-aikataulu {:urakka-id urakka-id
                                          :sopimus-id sopimus-id
                                          :vuosi vuosi}))

(defn hae-tiemerkinnan-suorittavat-urakat [urakka-id]
  (k/post! :hae-tiemerkinnan-suorittavat-urakat {:urakka-id urakka-id}))

(defn merkitse-kohde-valmiiksi-tiemerkintaan [kohde-id tiemerkintapvm urakka-id sopimus-id]
  (k/post! :merkitse-kohde-valmiiksi-tiemerkintaan {:kohde-id kohde-id
                                                    :tiemerkintapvm tiemerkintapvm
                                                    :urakka-id urakka-id
                                                    :sopimus-id sopimus-id}))

(defonce aikataulurivit
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @aikataulu-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (go
                  (sort-by tr-domain/tiekohteiden-jarjestys
                           (<! (hae-aikataulu valittu-urakka-id
                                              valittu-sopimus-id vuosi)))))))

(defonce tiemerkinnan-suorittavat-urakat
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               nakymassa? @aikataulu-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id nakymassa?)
                (hae-tiemerkinnan-suorittavat-urakat valittu-urakka-id))))

(defn- kohteen-aikataulun-tila [kentta-aloitettu kentta-valmis]
  (cond
    (and kentta-aloitettu
         (pvm/jalkeen? kentta-aloitettu (pvm/nyt))) :aloittamatta
    (and kentta-valmis
         (pvm/sama-tai-ennen? kentta-valmis (pvm/nyt))) :valmis
    (and kentta-valmis
         (pvm/jalkeen? kentta-valmis (pvm/nyt))) :kesken
    (and kentta-aloitettu
         (pvm/ennen? kentta-aloitettu (pvm/nyt))) :kesken

    :default :aloittamatta))

(defn luokittele-valmiuden-mukaan
  "Luokittelee annetun aikataulurivin valmiuden ja urakkatyypin mukaan kolmeen kategoriaan:
  :kohde-valmis, :kesken, :aloittamatta.

  Jos kohteen aloitus on tulevaisuudessa, palautetaan :aloittamatta
  Jos kohde on merkitty valmiiksi nyt tai menneisyydessä, palautetaan :valmis
  Jos kohde on merkitty valmiiksi tulevaisuudessa, palautetaan :kesken
  Jos kohde on aloitettu menneisyydessä, palautetaan :kesken
  Muuten palautetaan :aloittamatta"
  [{kohde-valmis :aikataulu-kohde-valmis
    kohde-aloitettu :aikataulu-kohde-alku
    tiemerkinta-aloitettu :aikataulu-tiemerkinta-alku
    tiemerkinta-lopetettu :aikataulu-tiemerkinta-loppu
    :as aikataulurivi} urakkatyyppi]
  (log "luokittele valmiuden mukjaan, ut: " (pr-str urakkatyyppi))
  (case urakkatyyppi
    (:paallystys :paikkaus)
    (kohteen-aikataulun-tila kohde-aloitettu kohde-valmis)

    :tiemerkinta
    (kohteen-aikataulun-tila tiemerkinta-aloitettu tiemerkinta-lopetettu)))

(defonce aikataulurivit-valmiuden-mukaan
  (reaction (group-by #(luokittele-valmiuden-mukaan % (:arvo @nav/urakkatyyppi))
                      @aikataulurivit)))

(defn tallenna-yllapitokohteiden-aikataulu [urakka-id sopimus-id vuosi kohteet]
  (go
    (let [vastaus (<! (k/post! :tallenna-yllapitokohteiden-aikataulu
                               {:urakka-id urakka-id
                                :sopimus-id sopimus-id
                                :vuosi vuosi
                                :kohteet kohteet}))]
      (reset! aikataulurivit vastaus))))
