(ns harja.tiedot.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [clojure.set :refer [rename-keys]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.asiakas.kommunikaatio :as k]
            [harja.atom :refer-macros [reaction<!] :refer [paivita-periodisesti]]
            [harja.pvm :as pvm]
            [cljs-time.core :as t]
            [harja.loki :refer [log]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-xf kartalla-esitettavaan-muotoon]]
            [harja.tiedot.navigaatio :as nav])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))
(defonce karttataso-tilannekuva (atom false))
(defonce mahdolliset-tilat #{:historiakuva :nykytilanne})
(defonce valittu-tila (atom :nykytilanne))

(defonce bufferi 1000)
(defonce hakutiheys (reaction (condp = @valittu-tila
                                :nykytilanne 3000
                                :historiakuva 60000)))

;; Mitä haetaan?
(defonce suodattimet {
                      :toimenpidepyynnot      "Toimenpidepyynnöt"
                      :kyselyt                "Kyselyt"
                      :tiedotukset            "Tiedotukset"
                      :turvallisuuspoikkeamat "Turvallisuuspoikkeamat"
                      :tarkastukset           "Tarkastukset"
                      :havainnot              "Havainnot"
                      :paikkaustyot           "Paikkaustyöt"
                      :paallystystyot         "Päällystystyöt"
                      :tyokoneet              "Työkoneet"
                      })
(defonce valitut-suodattimet (atom (into #{} (keys suodattimet))))

;; Haetaan/päivitetään toimenpidekoodit kun tullaan näkymään
(defonce toimenpidekoodit (reaction<! [nakymassa? @nakymassa?
                                       urakka @nav/valittu-urakka
                                       tyyppi @nav/valittu-urakkatyyppi]
                                      (when nakymassa?
                                        (go (let [res (<! (k/post! :hae-toimenpidekoodit-tilannekuvaan
                                                                   {:urakka        (:id urakka)
                                                                    :urakan-tyyppi (:arvo tyyppi)}))]
                                              (when-not (k/virhe? res) res))))))

;; Kartassa säilötään toteumakoodin nimi ja true/false arvo
(defonce valitut-toteumatyypit (atom {}))

;; Toimenpidekoodit-reaktio päivittyy aina kun tullaan näkymään tai kun urakka vaihtuu.
;; Tässä reaktiossa säilytetään kaikki toimenpidekoodit, jotka ovat relevantteja. Emme kuitenkaan halua että
;; aina urakkaa vaihdettaessa asetukset resetoituvat, jonka takia tarvitsemme tänän funktion.
(defonce valitse-uudet-tpkt
         (run!
           (let [nimet (into #{} (map :nimi @toimenpidekoodit))] ;; Mahdollisesti "uudet" toimenpidekoodit
             (swap! valitut-toteumatyypit
                    (fn [nyt-valitut]
                      (let [olemasaolevat (into #{} (keys nyt-valitut)) ;; "vanhat" valitut ja valitsemattomat toimenpidekoodit
                            kaikki (vec (clojure.set/union olemasaolevat nimet))] ;; Kaikki relevantit toimenpidekoodit
                        (zipmap kaikki
                                (map #(get nyt-valitut % true) ;; Jos ei löydy nyt-valituista, eli ei ole vanhaa arvoa,
                                     kaikki))))))))         ;; eli on uusi, niin laitetaan valituksi

(defonce naytettavat-toteumatyypit (reaction
                                     (group-by :emo @toimenpidekoodit)))

;; Valittu aikaväli vektorissa [alku loppu]
(defonce historiakuvan-aikavali (atom (pvm/kuukauden-aikavali (pvm/nyt))))

(defn- tunteja-vuorokausissa [vuorokaudet]
  (* 24 vuorokaudet))

(defn- tunteja-viikoissa [viikot]
  "Palauttaa montako tuntia on n viikossa."
  (tunteja-vuorokausissa (* 7 viikot)))

;; Mäppi sisältää numeroarvot tekstuaaliselle esitykselle.
(defonce aikasuodatin-tunteina {"2h"  2
                                "4h"  4
                                "24h" (tunteja-vuorokausissa 1)
                                "1vk" (tunteja-viikoissa 1)
                                "2vk" (tunteja-viikoissa 2)})

;; Nykytilanteeseen voidaan joutua tekemään eri suodatinvaihtoehtoja eri asioille,
;; esim talvella ja kesällä voi olla eri ajat. Siksi toteutettu tällä tavalla.
;; Vektori sisältää tekstuaalisen esityksen vaihtoehdot, nämä osuvat aikasuodatin-tunteina mäppiin. Atomissa
;; nykyinen valittu tekstuaalinen arvo.
(defonce talvikauden-aikasuodatin ["2h" "6h" "12h" "24h"])
(defonce valittu-aikasuodatin (atom talvikauden-aikasuodatin))
(defonce valittu-aikasuodattimen-arvo (reaction (first @valittu-aikasuodatin)))

(defonce haetut-asiat (atom nil))
(defonce tilannekuvan-asiat-kartalla
         (reaction
           @haetut-asiat
           (when @karttataso-tilannekuva
             (kartalla-esitettavaan-muotoon @haetut-asiat))))

(defn kasaa-parametrit []
  {:hallintayksikko @nav/valittu-hallintayksikko-id
   :urakka-id       (:id @nav/valittu-urakka)
   :alue            @nav/kartalla-nakyva-alue
   :alku            (if (= @valittu-tila :nykytilanne)
                      (pvm/nyt)
                      (first @historiakuvan-aikavali))
   :loppu           (if (= @valittu-tila :nykytilanne)
                      (t/plus (pvm/nyt) (t/hours (get aikasuodatin-tunteina @valittu-aikasuodattimen-arvo)))
                      (second @historiakuvan-aikavali))})

(defn hae-asiat []
  (log "Tilannekuva: Hae asiat (" (pr-str @valittu-tila) ")")
  (go
    (let [yhdista (fn [& tulokset]
                    (apply (comp vec concat) (remove k/virhe? tulokset)))
          yhteiset-parametrit (kasaa-parametrit)
          haettavat-toimenpidekoodit (mapv
                                       :id
                                       (filter
                                         (fn [{:keys [nimi]}]
                                           (get @valitut-toteumatyypit nimi))
                                         @toimenpidekoodit))
          tulos (yhdista
                  (when (and (= @valittu-tila :nykytilanne) (:tyokoneet @valitut-suodattimet))
                    (mapv
                      #(assoc % :tyyppi-kartalla :tyokone)
                      (let [tyokone-tulos (<! (k/post! :hae-tyokoneseurantatiedot yhteiset-parametrit))]
                        (when-not (k/virhe? tyokone-tulos) (tapahtumat/julkaise! {:aihe      :uusi-tyokonedata
                                                                                  :tyokoneet tyokone-tulos}))
                        tyokone-tulos)))                    ;;Voidaan palauttaa tässä vaikka olisi virhe - filtteröidään yhdista-funktiossa
                  (when (:turvallisuuspoikkeamat @valitut-suodattimet)
                    (mapv
                      #(assoc % :tyyppi-kartalla :turvallisuuspoikkeama)
                      (<! (k/post! :hae-turvallisuuspoikkeamat (rename-keys
                                                                 yhteiset-parametrit
                                                                 {:urakka :urakka-id})))))
                  (when (:tarkastukset @valitut-suodattimet)
                    (mapv
                      #(assoc % :tyyppi-kartalla :tarkastus)
                      (<! (k/post! :hae-urakan-tarkastukset (rename-keys
                                                              yhteiset-parametrit
                                                              {:urakka :urakka-id
                                                               :alku   :alkupvm
                                                               :loppu  :loppupvm})))))
                  (when (:havainnot @valitut-suodattimet)
                    (mapv
                      #(assoc % :tyyppi-kartalla :havainto)
                      (<! (k/post! :hae-urakan-havainnot (rename-keys
                                                           yhteiset-parametrit
                                                           {:urakka :urakka-id})))))
                  (when (:paikkaustyot @valitut-suodattimet)
                    (remove
                      #(empty? (:kohdeosat %))
                      (mapv
                        #(assoc % :tyyppi-kartalla :paikkaustoteuma)
                        (<! (k/post! :urakan-paikkaustoteumat (rename-keys
                                                                yhteiset-parametrit
                                                                {:urakka :urakka-id}))))))
                  (when (:paallystystyot @valitut-suodattimet)
                    (remove
                      #(empty? (:kohdeosat %))
                      (mapv
                        #(assoc % :tyyppi-kartalla :paallystyskohde)
                        (<! (k/post! :urakan-paallystyskohteet (rename-keys
                                                                 yhteiset-parametrit
                                                                 {:urakka :urakka-id}))))))
                  (when
                    (or (:toimenpidepyynnot @valitut-suodattimet)
                        (:kyselyt @valitut-suodattimet)
                        (:tiedotukset @valitut-suodattimet))
                    (mapv
                      #(assoc % :tyyppi-kartalla (:ilmoitustyyppi %))
                      (<! (k/post! :hae-ilmoitukset (assoc
                                                      yhteiset-parametrit
                                                      :aikavali [(:alku yhteiset-parametrit)
                                                                 (:loppu yhteiset-parametrit)]
                                                      :tilat #{:avoimet}
                                                      :tyypit (remove nil? [(when (:toimenpidepyynnot @valitut-suodattimet)
                                                                              :toimenpidepyynto)
                                                                            (when (:kyselyt @valitut-suodattimet)
                                                                              :kysely)
                                                                            (when (:tiedotukset @valitut-suodattimet)
                                                                              :tiedoitus)]))))))
                  (when-not (empty? haettavat-toimenpidekoodit)
                    (mapv
                      #(assoc % :tyyppi-kartalla :toteuma)
                      (<! (k/post! :hae-toteumat-tilannekuvaan (assoc
                                                                 yhteiset-parametrit
                                                                 :toimenpidekoodit
                                                                 haettavat-toimenpidekoodit))))))]
      (reset! haetut-asiat tulos))))

(def asioiden-haku (reaction<!
                     [_ @valitut-suodattimet
                      _ @valitut-toteumatyypit
                      _ @nav/kartalla-nakyva-alue
                      _ @nav/valittu-urakka
                      nakymassa? @nakymassa?
                      _ @nav/valittu-hallintayksikko-id]
                     {:odota bufferi}
                     (when nakymassa? (hae-asiat))))

(defonce lopeta-haku (atom nil))                            ;; Säilöö funktion jolla pollaus lopetetaan

(defonce pollaus
         (run! (if @nakymassa?
                 (do
                   (when @lopeta-haku (@lopeta-haku))
                   (log "Tilannekuva: Aloitetaan haku (tai päivitetään tiheyttä)")
                   (reset! lopeta-haku (paivita-periodisesti asioiden-haku @hakutiheys)))

                 (when @lopeta-haku (do
                                      (@lopeta-haku)
                                      (log "Tilannekuva: Lopetetaan haku")
                                      (reset! lopeta-haku nil))))))