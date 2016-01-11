(ns harja.tiedot.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.asiakas.kommunikaatio :as k]
            [harja.views.kartta :as kartta]
            [harja.tiedot.tilannekuva.tilannekuva-kartalla :as tilannekuva-kartalla]
            [harja.atom :refer-macros [reaction<!] :refer [paivita-periodisesti]]
            [harja.pvm :as pvm]
            [cljs-time.core :as t]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.navigaatio :as nav])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))
(defonce valittu-tila (atom :nykytilanne))

;; odota sekunti ennen hakemista kun muutoksia tehdään
(defonce bufferi 1000)

;; 10s riittää jos näkymä on paikallaan, tiedot haetaan heti uudelleen, jos
;; karttaa siirretään tai zoomataan
(defonce hakutiheys-nykytilanne 10000)

(defonce hakutiheys-historiakuva 1200000)

;; Jokaiselle suodattimelle teksti, jolla se esitetään käyttöliittymässä
(defonce suodattimien-nimet
         {:laatupoikkeamat                  "Laatupoikkeamat"
          :tilaaja                          "Tilaaja"
          :urakoitsija                      "Urakoitsija"
          :konsultti                        "Konsultti"

          :tarkastukset                     "Tarkastukset"
          :tiesto                           "Tiestö"
          :talvihoito                       "Talvihoito"
          :soratie                          "Soratie"
          :laatu                            "Laatu"
          :pistokoe                         "Pistokoe"

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
          "pinnan tasaus"                   "Pinnan tasaus"
          "liikennemerkkien puhdistus"      "Liikennemerkkien puhdistus"
          "lumivallien madaltaminen"        "Lumivallien madaltaminen"
          "sulamisveden haittojen torjunta" "Sulamisveden haittojen torjunta"
          "tiestotarkastus"                 "Tiestötarkastus"
          "kelintarkastus"                  "Kelintarkastus"
          "harjaus"                         "Harjaus"
          "koneellinen niitto"              "Koneellinen niitto"
          "koneellinen vesakonraivaus"      "Koneellinen vesakonraivaus"
          "sorateiden muokkaushoylays"      "Sorateiden muokkaushöyläys"
          "sorateiden polynsidonta"         "Sorateiden pölynsidonta"
          "sorateiden tasaus"               "Sorateiden tasaus"
          "sorastus"                        "Sorastus"
          "paallysteiden paikkaus"          "Päällysteiden paikkaus"
          "paallysteiden juotostyot"        "Päällysteiden juotostyöt"
          "siltojen puhdistus"              "Siltojen puhdistus"
          "l- ja p-alueiden puhdistus"      "L- ja P-alueiden puhdistus"
          "muu"                             "Muu"
          "liuossuolaus"                    "Liuossuolaus"
          "aurausviitoitus ja kinostimet"   "Aurausviitoitus ja kinostimet"
          "lumensiirto"                     "Lumensiirto"
          "paannejaan poisto"               "Paannejään poisto"})

;; Kartassa säilötään suodattimien tila, valittu / ei valittu.
(defonce suodattimet (atom {:yllapito        {:paallystys true
                                              :paikkaus   true}
                            :ilmoitukset     {:tyypit {:toimenpidepyynto true
                                                       :kysely           true
                                                       :tiedoitus        true}
                                              :tilat  #{:avoimet}}
                            :turvallisuus    {:turvallisuuspoikkeamat true}
                            :laatupoikkeamat {:tilaaja     true
                                              :urakoitsija true
                                              :konsultti   true}
                            :tarkastukset    {:tiesto     true
                                              :talvihoito true
                                              :soratie    true
                                              :laatu      true
                                              :pistokoe   true}
                            ;; Näiden pitää osua työkoneen enumeihin
                            :talvi           {"auraus ja sohjonpoisto"          true
                                              "suolaus"                         true
                                              "pistehiekoitus"                  true
                                              "linjahiekoitus"                  true
                                              "lumivallien madaltaminen"        true
                                              "sulamisveden haittojen torjunta" true
                                              "kelintarkastus"                  true
                                              "liuossuolaus"                    true
                                              "aurausviitoitus ja kinostimet"   true
                                              "lumensiirto"                     true
                                              "paannejaan poisto"               true
                                              "muu"                             true}
                            :kesa            {"tiestotarkastus"            true
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

(defn- tunteja-vuorokausissa [vuorokaudet]
  (* 24 vuorokaudet))

(defn- tunteja-viikoissa [viikot]
  "Palauttaa montako tuntia on n viikossa."
  (tunteja-vuorokausissa (* 7 viikot)))

;; Mäppi sisältää numeroarvot tekstuaaliselle esitykselle.
(defonce nykytilanteen-aikasuodatin-tunteina [["0-2h" 2]
                                              ["0-4h" 4]
                                              ["0-12h" 12]
                                              ["1 vrk" (tunteja-vuorokausissa 1)]
                                              ["2 vrk" (tunteja-vuorokausissa 2)]
                                              ["3 vrk" (tunteja-vuorokausissa 3)]
                                              ["1 vk" (tunteja-viikoissa 1)]
                                              ["2 vk" (tunteja-viikoissa 2)]
                                              ["3 vk" (tunteja-viikoissa 3)]])

(defonce historiakuvan-aikavali (atom (pvm/kuukauden-aikavali (pvm/nyt)))) ;; Valittu aikaväli vektorissa [alku loppu]
(defonce nykytilanteen-aikasuodattimen-arvo (atom 2))

(defn kasaa-parametrit []
  (merge
    {:hallintayksikko (:id @nav/valittu-hallintayksikko)
     :urakka-id       (:id @nav/valittu-urakka)
     :urakoitsija     (:id @nav/valittu-urakoitsija)
     :urakkatyyppi    (:arvo @nav/valittu-urakkatyyppi)
     :nykytilanne?    (= :nykytilanne @valittu-tila)
     :alue            @nav/kartalla-nakyva-alue
     :alku            (if (= @valittu-tila :nykytilanne)
                        (t/minus (pvm/nyt) (t/hours @nykytilanteen-aikasuodattimen-arvo))
                        (first @historiakuvan-aikavali))
     :loppu           (if (= @valittu-tila :nykytilanne)
                        (pvm/nyt)
                        (second @historiakuvan-aikavali))}
    @suodattimet))

(defn yhdista-tyokonedata [uusi]
  (let [vanhat (:tyokoneet @tilannekuva-kartalla/haetut-asiat)
        uudet (:tyokoneet uusi)
        uudet-idt (into #{} (keys uudet))]
    (assoc uusi :tyokoneet
                (into {}
                      (filter (fn [[id _]]
                                (uudet-idt id))
                              (merge-with
                                (fn [vanha uusi]
                                  (let [vanha-reitti (:reitti vanha)]
                                    (assoc uusi :reitti (if (= (:sijainti vanha) (:sijainti uusi))
                                                          vanha-reitti
                                                          (conj
                                                            (or vanha-reitti [(:sijainti vanha)])
                                                            (:sijainti uusi))))))
                                vanhat uudet))))))

  (def edellisen-haun-kayttajan-suodattimet (atom {:tila                 @valittu-tila
                                                   :aikavali-nykytilanne @nykytilanteen-aikasuodattimen-arvo
                                                   :aikavali-historia    @historiakuvan-aikavali
                                                   :suodattimet          @suodattimet}))

  (def tyhjenna-popupit-kun-filtterit-muuttuu (run!
                                                @valittu-tila
                                                @nykytilanteen-aikasuodattimen-arvo
                                                @historiakuvan-aikavali
                                                @suodattimet
                                                (kartta/poista-popup!)))

(defn hae-asiat []
  (log "Tilannekuva: Hae asiat (" (pr-str @valittu-tila) ")")
  (go
    ;; Asetetaan kartalle "Päivitetään karttaa" viesti jos haku tapahtui käyttäjän vaihdettua suodattimia
    (when (or (not= @valittu-tila (:tila @edellisen-haun-kayttajan-suodattimet))
              (not= @nykytilanteen-aikasuodattimen-arvo (:aikavali-nykytilanne @edellisen-haun-kayttajan-suodattimet))
              (not= @historiakuvan-aikavali (:aikavali-historia @edellisen-haun-kayttajan-suodattimet))
              (not= @suodattimet (:suodattimet @edellisen-haun-kayttajan-suodattimet)))
      (reset! edellisen-haun-kayttajan-suodattimet {:tila                 @valittu-tila
                                                    :aikavali-nykytilanne @nykytilanteen-aikasuodattimen-arvo
                                                    :aikavali-historia    @historiakuvan-aikavali
                                                    :suodattimet          @suodattimet})
      (kartta/aseta-paivitetaan-karttaa-tila true))
    (let [yhteiset-parametrit (kasaa-parametrit)
          julkaise-tyokonedata! (fn [tulos]
                                  (tapahtumat/julkaise! {:aihe      :uusi-tyokonedata
                                                         :tyokoneet (vals (:tyokoneet tulos))})
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
      (reset! tilannekuva-kartalla/haetut-asiat tulos)
      (kartta/aseta-paivitetaan-karttaa-tila false))))

(def asioiden-haku (reaction<!
                    [_ @valittu-tila
                     _ @suodattimet
                     _ @nykytilanteen-aikasuodattimen-arvo
                     _ @historiakuvan-aikavali
                     _ @nav/kartalla-nakyva-alue
                     _ @nav/valittu-urakka
                     nakymassa? @nakymassa?
                     _ @nav/valittu-hallintayksikko]
                    {:odota bufferi}
                    (when nakymassa?
                      (hae-asiat))))

(defonce lopeta-haku (atom nil))                          ;; Säilöö funktion jolla pollaus lopetetaan

(defn aloita-periodinen-haku []
  (log "Tilannekuva: Aloitetaan haku")
  (reset! lopeta-haku (paivita-periodisesti asioiden-haku (case @valittu-tila
                                                            :nykytilanne hakutiheys-nykytilanne
                                                            :historiakuva hakutiheys-historiakuva))))

(defn lopeta-periodinen-haku-jos-kaynnissa []
  (when @lopeta-haku
    (log "Tilannekuva: Lopetetaan haku")
    (@lopeta-haku)
    (reset! lopeta-haku nil)))

(defn pollaus-muuttui []
  (let [nakymassa? @nakymassa?
        valittu-tila @valittu-tila]
    (log "nakymassa? " nakymassa? "; valittu-tila: " (pr-str valittu-tila))
    (if nakymassa?
      (do
        (lopeta-periodinen-haku-jos-kaynnissa)
        (aloita-periodinen-haku))
      (lopeta-periodinen-haku-jos-kaynnissa))))


(add-watch nakymassa? :pollaus-muuttui
           (fn [_ _ old new]
             (log "nakymassa? muuttui " old " => " new )
             (pollaus-muuttui)))
(add-watch valittu-tila :pollaus-muuttui
           (fn [_ _ old new]
             (log "valittu-tila muuttui " old " => " new )
             (pollaus-muuttui)))


