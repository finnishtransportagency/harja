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

(def ^{:doc   "Aika joka odotetaan ennen uusien tietojen hakemista, kun parametrit muuttuvat"
       :const true}
bufferi 1000)

;; 10s riittää jos näkymä on paikallaan, tiedot haetaan heti uudelleen, jos
;; karttaa siirretään tai zoomataan
(def ^{:doc   "Päivitystiheys tilanenkuvassa, kun parametrit eivät muutu"
       :const true}
hakutiheys-nykytilanne 10000)

(def ^{:doc   "Päivitystiheys historiakuvassa on 20 minuuttia."
       :const true}
hakutiheys-historiakuva 1200000)

;; Jokaiselle suodattimelle teksti, jolla se esitetään käyttöliittymässä
(def suodattimien-nimet
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

(def jarjestys
  {:talvi ["auraus ja sohjonpoisto"
           "suolaus"
           "pistehiekoitus"
           "linjahiekoitus"
           "lumivallien madaltaminen"
           "sulamisveden haittojen torjunta"
           "liuossuolaus"
           "aurausviitoitus ja kinostimet"
           "lumensiirto"
           "paannejaan poisto"
           "muu"]
   :kesa  ["harjaus"
           "sorastus"
           "sorateiden muokkaushoylays"
           "sorateiden polynsidonta"
           "sorateiden tasaus"
           "pinnan tasaus"
           "koneellinen niitto"
           "koneellinen vesakonraivaus"
           "liikennemerkkien puhdistus"
           "paallysteiden paikkaus"
           "paallysteiden juotostyot"
           "siltojen puhdistus"
           "l- ja p-alueiden puhdistus"
           "muu"]})

;; Kartassa säilötään suodattimien tila, valittu / ei valittu.
(defonce suodattimet (atom {:yllapito        {:paallystys false
                                              :paikkaus   false}
                            :ilmoitukset     {:tyypit {:toimenpidepyynto false
                                                       :kysely           false
                                                       :tiedoitus        false}
                                              :tilat  #{:avoimet}}
                            :turvallisuus    {:turvallisuuspoikkeamat false}
                            :laatupoikkeamat {:tilaaja     false
                                              :urakoitsija false
                                              :konsultti   false}
                            :tarkastukset    {:tiesto     false
                                              :talvihoito false
                                              :soratie    false
                                              :laatu      false
                                              :pistokoe   false}
                            ;; Näiden pitää osua työkoneen enumeihin
                            ;; Kelintarkastus ja tiestotarkastus liittyvät tarkastusten tekoon,
                            ;; eivät ole "toteumia". Säilytetty kommenteissa, jotta JOS tarkasten
                            ;; tekoa halutaan seurana livenä, niin arvot on täällä valmiiksi copypastettavissa..
                            :talvi           {"auraus ja sohjonpoisto"          false
                                              "suolaus"                         false
                                              "pistehiekoitus"                  false
                                              "linjahiekoitus"                  false
                                              "lumivallien madaltaminen"        false
                                              "sulamisveden haittojen torjunta" false
                                              ;;"kelintarkastus"                  false
                                              "liuossuolaus"                    false
                                              "aurausviitoitus ja kinostimet"   false
                                              "lumensiirto"                     false
                                              "paannejaan poisto"               false
                                              "muu"                             false}
                            :kesa            {;;"tiestotarkastus"            false
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

(defn kartan-tyypiksi [t avain tyyppi]
  (assoc t avain (map #(assoc % :tyyppi-kartalla tyyppi) (avain t))))

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
      (kartta/aseta-paivitetaan-karttaa-tila! true))

    (let [yhteiset-parametrit (kasaa-parametrit)
          julkaise-tyokonedata! (fn [tulos]
                                  (tapahtumat/julkaise! {:aihe      :uusi-tyokonedata
                                                         :tyokoneet (vals (:tyokoneet tulos))})
                                  tulos)
          tulos (-> (<! (k/post! :hae-tilannekuvaan yhteiset-parametrit))
                    (yhdista-tyokonedata)
                    (julkaise-tyokonedata!))]
      (reset! tilannekuva-kartalla/haetut-asiat tulos)
      (kartta/aseta-paivitetaan-karttaa-tila! false))))

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

(defonce lopeta-haku (atom nil))                            ;; Säilöö funktion jolla pollaus lopetetaan

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
             (log "nakymassa? muuttui " old " => " new)
             (pollaus-muuttui)

             ;; Jos tilannekuvasta on poistuttu, tyhjennetään haetut-asiat.
             ;; Tämä poistaa kaikki tilannekuvan karttatasot.
             (when (false? new)
               (reset! tilannekuva-kartalla/haetut-asiat nil))))
(add-watch valittu-tila :pollaus-muuttui
           (fn [_ _ old new]
             (log "valittu-tila muuttui " old " => " new)
             (pollaus-muuttui)))


