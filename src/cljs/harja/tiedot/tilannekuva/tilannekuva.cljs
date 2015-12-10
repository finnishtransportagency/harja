(ns harja.tiedot.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [clojure.set :refer [rename-keys]]
            [harja.loki :refer [log tarkkaile!]]
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
(def suodattimet (atom {:yllapito       {:paallystys false
                                         :paikkaus   false}
                        :ilmoitukset    {:tyypit {:toimenpidepyynto false
                                                  :kysely           false
                                                  :tiedoitus        false}
                                         :tilat  #{:avoimet}}
                        :turvallisuus   {:turvallisuuspoikkeamat false}
                        :laadunseuranta {:laatupoikkeamat false
                                         :tarkastukset    false}

                        ;; Näiden pitää osua työkoneen enumeihin
                        :talvi          {"auraus ja sohjonpoisto"          true
                                         "suolaus"                         true
                                         "pistehiekoitus"                  true
                                         "linjahiekoitus"                  true
                                         "lumivallien madaltaminen"        true
                                         "sulamisveden haittojen torjunta" true
                                         "kelintarkastus"                  true
                                         "muu"                             true}

                        :kesa           {"tiestotarkastus"            true
                                         "koneellinen niitto"         true
                                         "koneellinen vesakonraivaus" true

                                         "liikennemerkkien puhdistus" true

                                         "sorateiden muokkaushoylays" true
                                         "sorateiden polynsidonta"    true
                                         "sorateiden tasaus"          true
                                         "sorastus"                   true

                                         "harjaus"                    true
                                         "pinnan tasaus"              true
                                         "paallysteiden paikkaus"     true
                                         "paallysteiden juotostyot"   true

                                         "siltojen puhdistus"         true

                                         "l- ja p-alueiden puhdistus" true
                                         "muu"                        true}}))
(tarkkaile! "Suodattimet " suodattimet)

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
             (kartalla-esitettavaan-muotoon
               (concat (vals (:tyokoneet @haetut-asiat)) (apply concat (vals (dissoc @haetut-asiat :tyokoneet))))))))

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

(defn yhdista-tyokonedata [uusi]
  (let [vanhat (:tyokoneet @haetut-asiat)
        uudet (:tyokoneet uusi)]
    (log "Yhdistä työkonedata: " (pr-str uusi))
    (log (pr-str vanhat))
    (log (pr-str uudet))
    (assoc uusi :tyokoneet
                (merge-with
                  (fn [vanha uusi]
                    (let [vanha-reitti (:reitti vanha)]
                      (assoc uusi :reitti (if (= (:sijainti vanha) (:sijainti uusi))
                                            vanha-reitti
                                            (conj
                                              (or vanha-reitti [(:sijainti vanha)])
                                              (:sijainti uusi))))))
                  vanhat uudet))))

(defn hae-asiat []
  (log "Tilannekuva: Hae asiat (" (pr-str @valittu-tila) ")")
  (go
    (let [yhteiset-parametrit (kasaa-parametrit)
          julkaise-tyokonedata! (fn [tulos]
                                  (tapahtumat/julkaise! {:aihe      :uusi-tyokonedata
                                                         :tyokoneet (:tyokoneet tulos)})
                                  tulos)
          lisaa-karttatyypit (fn [tulos]
                               (as-> tulos t
                                     (assoc t :ilmoitukset
                                              (map #(assoc % :tyyppi-kartalla (:ilmoitustyyppi %))
                                                   (:ilmoitukset t)))
                                     (assoc t :turvallisuuspoikkeamat
                                              (map #(assoc % :tyyppi-kartalla :turvallisuuspoikkeama)
                                                   (:turvallisuuspoikkeamat t)))
                                     (assoc t :tarkastukset
                                              (map #(assoc % :tyyppi-kartalla :tarkastus)
                                                   (:tarkastukset t)))
                                     (assoc t :laatupoikkeamat
                                              (map #(assoc % :tyyppi-kartalla :laatupoikkeama)
                                                   (:laatupoikkeamat t)))
                                     (assoc t :paikkaus
                                              (map #(assoc % :tyyppi-kartalla :paikkaus)
                                                   (:paikkaus t)))
                                     (assoc t :paallystys
                                              (map #(assoc % :tyyppi-kartalla :paallystys)
                                                   (:paallystys t)))

                                     ;; Tyokoneet on mäp, id -> työkone
                                     (assoc t :tyokoneet (into {}
                                                               (map
                                                                 (fn [[id tyokone]]
                                                                   {id (assoc tyokone :tyyppi-kartalla :tyokone)})
                                                                 (:tyokoneet t))))

                                     (assoc t :toteumat
                                              (map #(assoc % :tyyppi-kartalla :toteuma)
                                                   (:toteumat t)))))

          tulos (-> (<! (k/post! :hae-tilannekuvaan yhteiset-parametrit))
                    (yhdista-tyokonedata)
                    (julkaise-tyokonedata!)
                    (lisaa-karttatyypit))]
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