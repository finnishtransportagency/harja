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
(defonce valittu-tila (atom :nykytilanne))

(defonce bufferi 1000)
(defonce hakutiheys (reaction (condp = @valittu-tila
                                :nykytilanne 3000
                                :historiakuva 60000)))

;; Jokaiselle suodattimelle teksti, jolla se esitetään käyttöliittymässä
(defonce suodattimien-nimet
         {:laatupoikkeamat                  "Laatupoikkeamat"
          :tarkastukset                     "Tarkastukset"
          :turvallisuuspoikkeamat           "Turvallisuuspoikkeamat"

          :toimenpidepyynto                 "TPP"
          :tiedoitus                        "TUR"
          :kysely                           "URK"

          :paallystys                       "Päällystystyöt"
          :paikkaus                         "Paikkaustyöt"

          "auraus ja sohjonpoisto"          "Auraus ja sohjonpoisto"
          "suolaus"                         "Suolaus"
          "pistehiekoitus"                  "Pistehiekoitus"
          "linjahiekoitus"                  "Linjahiekoitus"
          "lumivallien madaltaminen"        "Lumivallien madaltaminen"
          "sulamisveden haittojen torjunta" "Sulamisveden haittojen torjunta"
          "kelintarkastus"                  "Kelintarkastus"

          "tiestotarkastus"                 "Tiestötarkastus"
          "koneellinen niitto"              "Koneellinen niitto"
          "koneellinen vesakonraivaus"      "Koneellinen vesakonraivaus"

          "liikennemerkkien puhdistus"      "Liikennemerkkien puhdistus"

          "sorateiden muokkaushoylays"      "Sorateiden muokkaushöyläys"
          "sorateiden polynsidonta"         "Sorateiden pölynsidonta"
          "sorateiden tasaus"               "Sorateiden tasaus"
          "sorastus"                        "Sorastus"

          "harjaus"                         "Harjaus"
          "pinnan tasaus"                   "Pinnan tasaus"
          "paallysteiden paikkaus"          "Päällysteiden paikkaus"
          "paallysteiden juotostyot"        "Päällysteiden juotostyöt"

          "siltojen puhdistus"              "Siltojen puhdistus"

          "l- ja p-alueiden puhdistus"      "L- ja P-alueiden puhdistus"
          "muu"                             "Muu"})

;; Kartassa säilötään suodattimien tila, valittu / ei valittu.
(defonce suodattimet (atom {:yllapito       {:paallystys true
                                             :paikkaus   false}
                            :ilmoitukset    {:tyypit {:toimenpidepyynto false
                                                      :kysely           false
                                                      :tiedoitus        false}
                                             :tilat  #{:avoimet}}
                            :turvallisuus   {:turvallisuuspoikkeamat false}
                            :laadunseuranta {:laatupoikkeamat false
                                             :tarkastukset    false}

                            ;; Näiden pitää osua työkoneen enumeihin
                            :talvi          {"auraus ja sohjonpoisto"          false
                                             "suolaus"                         false
                                             "pistehiekoitus"                  false
                                             "linjahiekoitus"                  false
                                             "lumivallien madaltaminen"        false
                                             "sulamisveden haittojen torjunta" false
                                             "kelintarkastus"                  false
                                             "muu"                             false}

                            :kesa           {"tiestotarkastus"            false
                                             "koneellinen niitto"         false
                                             "koneellinen vesakonraivaus" false

                                             "liikennemerkkien puhdistus" false

                                             "sorateiden muokkaushoylays" false
                                             "sorateiden polynsidonta"    false
                                             "sorateiden tasaus"          false
                                             "sorastus"                   false

                                             "harjaus"                    false
                                             "pinnan tasaus"              false
                                             "paallysteiden paikkaus"     false
                                             "paallysteiden juotostyot"   false

                                             "siltojen puhdistus"         false

                                             "l- ja p-alueiden puhdistus" false
                                             "muu"                        false}}))

;; Valittu aikaväli vektorissa [alku loppu]
(defonce historiakuvan-aikavali (atom (pvm/kuukauden-aikavali (pvm/nyt))))

(defn- tunteja-vuorokausissa [vuorokaudet]
  (* 24 vuorokaudet))

(defn- tunteja-viikoissa [viikot]
  "Palauttaa montako tuntia on n viikossa."
  (tunteja-vuorokausissa (* 7 viikot)))

;; Mäppi sisältää numeroarvot tekstuaaliselle esitykselle.
(defonce aikasuodatin-tunteina [["0-2h" 2]
                                ["0-4h" 4]
                                ["0-12h" 12]
                                ["1 vrk" (tunteja-vuorokausissa 1)]
                                ["2 vrk" (tunteja-vuorokausissa 2)]
                                ["3 vrk" (tunteja-vuorokausissa 3)]
                                ["1 vk" (tunteja-viikoissa 1)]
                                ["2 vk" (tunteja-viikoissa 2)]
                                ["3 vk" (tunteja-viikoissa 3)]])

(defonce valitun-aikasuodattimen-arvo (atom (tunteja-viikoissa 520)))

(defonce haetut-asiat (atom nil))
(defonce tilannekuvan-asiat-kartalla
         (reaction
           @haetut-asiat
           (when @karttataso-tilannekuva
             (kartalla-esitettavaan-muotoon @haetut-asiat))))

(defn kasaa-parametrit []
  (merge
    {:hallintayksikko @nav/valittu-hallintayksikko-id
     :urakka-id       (:id @nav/valittu-urakka)
     :alue            @nav/kartalla-nakyva-alue
     :alku            (if (= @valittu-tila :nykytilanne)
                        (t/minus (pvm/nyt) (t/hours @valitun-aikasuodattimen-arvo))
                        (first @historiakuvan-aikavali))
     :loppu           (if (= @valittu-tila :nykytilanne)
                        (pvm/nyt)
                        (second @historiakuvan-aikavali))}
    @suodattimet))

(defn hae-asiat []
  (log "Tilannekuva: Hae asiat (" (pr-str @valittu-tila) ")")
  (go
    (let [yhteiset-parametrit (kasaa-parametrit)
          julkaise-tyokonedata! (fn [tulos]
                                  ;; TODO: lisää tänne logiikkaa, jolla tunnistetaan onko uutta työkonedataa tullut
                                  tulos)                    ; Muista palauttaa sama tulos
          lisaa-karttatyypit (fn [tulos]
                               (-> tulos
                                   (assoc :ilmoitukset
                                          (map #(assoc % :tyyppi-kartalla (:ilmoitustyyppi %))
                                               (:ilmoitukset tulos)))
                                   (assoc :turvallisuuspoikkeamat
                                          (map #(assoc % :tyyppi-kartalla :turvallisuuspoikkeama)
                                               (:turvallisuuspoikkeamat tulos)))
                                   (assoc :tarkastukset
                                          (map #(assoc % :tyyppi-kartalla :tarkastus)
                                               (:tarkastukset tulos)))
                                   (assoc :laatupoikkeamat
                                          (map #(assoc % :tyyppi-kartalla :laatupoikkeama)
                                               (:laatupoikkeamat tulos)))
                                   (assoc :paikkaus
                                          (map #(assoc % :tyyppi-kartalla :paikkaus)
                                               (:paikkaus tulos)))
                                   (assoc :paallystys
                                          (map #(assoc % :tyyppi-kartalla :paallystys)
                                               (:paallystys tulos)))))
          yhdista (fn [kartta]
                    (when-not (k/virhe? kartta)
                      (apply (comp vec concat) (remove
                                                 (fn [a]
                                                   (or
                                                     (k/virhe? a)
                                                     (nil? a)
                                                     (nil? (first a))))
                                                 (vals kartta)))))
          tulos (-> (<! (k/post! :hae-tilannekuvaan yhteiset-parametrit))
                    (julkaise-tyokonedata!)
                    (lisaa-karttatyypit)
                    (yhdista))]
      (reset! haetut-asiat tulos))))

#_(defn hae-asiat []
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
                          tyokone-tulos)))                  ;;Voidaan palauttaa tässä vaikka olisi virhe - filtteröidään yhdista-funktiossa
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
                     [;;_ @valitut-suodattimet
                      ;;_ @valitut-toteumatyypit
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