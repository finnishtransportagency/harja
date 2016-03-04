(ns harja.tiedot.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.asiakas.kommunikaatio :as k]
            [harja.views.kartta :as kartta]
            [harja.tiedot.tilannekuva.tilannekuva-kartalla
             :as tilannekuva-kartalla]
            [harja.atom :refer-macros [reaction<!] :refer [paivita-periodisesti]]
            [harja.pvm :as pvm]
            [cljs-time.core :as t]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.tilannekuva :as tk])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))
(defonce valittu-tila (atom :nykytilanne))

(def ^{:doc   "Aika joka odotetaan ennen uusien tietojen hakemista, kun
 parametrit muuttuvat"
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

(def ilmoitusten-tilat-nykytilanteessa #{:kuittaamaton :vastaanotto :aloitus :muutos :vastaus})
(def ilmoitusten-tilat-historiakuvassa #{:kuittaamaton :vastaanotto :aloitus :lopetus :muutos :vastaus})

;; Kartassa säilötään suodattimien tila, valittu / ei valittu.
(defonce suodattimet
  (atom {:yllapito        {tk/paallystys false
                           tk/paikkaus   false}
         :ilmoitukset     {:tyypit {tk/tpp false
                                    tk/urk false
                                    tk/tur false}
                           :tilat  ilmoitusten-tilat-nykytilanteessa}
         :turvallisuus    {tk/turvallisuuspoikkeamat false}
         :laatupoikkeamat {tk/laatupoikkeama-tilaaja     false
                           tk/laatupoikkeama-urakoitsija false
                           tk/laatupoikkeama-konsultti   false}
         :tarkastukset    {tk/tarkastus-tiesto     false
                           tk/tarkastus-talvihoito false
                           tk/tarkastus-soratie    false
                           tk/tarkastus-laatu      false
                           tk/tarkastus-pistokoe   false}
         ;; Näiden pitää osua työkoneen enumeihin
         ;; Kelintarkastus ja tiestotarkastus liittyvät tarkastusten tekoon,
         ;; eivät ole "toteumia". Säilytetty kommenteissa, jotta JOS tarkasten
         ;; tekoa halutaan seurana livenä, niin arvot on täällä valmiiksi
         ;; copypastettavissa..
         :talvi {tk/auraus-ja-sohjonpoisto          false
                 tk/suolaus                         false
                 tk/pistehiekoitus                  false
                 tk/linjahiekoitus                  false
                 tk/lumivallien-madaltaminen        false
                 tk/sulamisveden-haittojen-torjunta false
                 tk/liuossuolaus                    false
                 tk/aurausviitoitus-ja-kinostimet   false
                 tk/lumensiirto                     false
                 tk/paannejaan-poisto               false
                 tk/muu                             false

                 ;; Pinnan tasaus on mielestämme kesätoimenpide, mutta Anne
                 ;; mailissaan pyysi, että pinnan tasaus tulee myös
                 ;; talvitoimenpiteisiin. Maili liittyi suodattimien
                 ;; järjestykseen. Pyysin tarkennusta, mutta
                 ;; päätin commitoida tämän talteen ettei vaan pääse unohtumaan.
                 tk/pinnan-tasaus                   false}
         :kesa {tk/koneellinen-niitto         false
                tk/koneellinen-vesakonraivaus false
                tk/liikennemerkkien-puhdistus false
                tk/sorateiden-muokkaushoylays false
                tk/sorateiden-polynsidonta    false
                tk/sorateiden-tasaus          false
                tk/sorastus                   false
                tk/harjaus                    false
                tk/pinnan-tasaus              false
                tk/paallysteiden-paikkaus     false
                tk/paallysteiden-juotostyot   false
                tk/siltojen-puhdistus         false
                tk/l-ja-p-alueiden-puhdistus  false
                tk/muu                        false}}))

(def jarjestys tk/jarjestys)

(defn- tunteja-vuorokausissa [vuorokaudet]
  (* 24 vuorokaudet))

(defn- tunteja-viikoissa [viikot]
  "Palauttaa montako tuntia on n viikossa."
  (tunteja-vuorokausissa (* 7 viikot)))

;; Mäppi sisältää numeroarvot tekstuaaliselle esitykselle.
(defonce nykytilanteen-aikasuodatin-tunteina
  [["0-2h" 2]
   ["0-4h" 4]
   ["0-12h" 12]
   ["1 vrk" (tunteja-vuorokausissa 1)]
   ["2 vrk" (tunteja-vuorokausissa 2)]
   ["3 vrk" (tunteja-vuorokausissa 3)]
   ["1 vk" (tunteja-viikoissa 1)]
   ["2 vk" (tunteja-viikoissa 2)]
   ["3 vk" (tunteja-viikoissa 3)]])

(defonce historiakuvan-aikavali
   ;; Valittu aikaväli vektorissa [alku loppu]
  (atom (pvm/kuukauden-aikavali (pvm/nyt))))

(defonce nykytilanteen-aikasuodattimen-arvo (atom 2))

(defn kasaa-parametrit [hallintayksikko urakka urakoitsija urakkatyyppi tila
                        nakyva-alue nykytilanteen-aikasuodattimen-arvo
                        historiakuvan-aikavali suodattimet]
  (merge
   {:hallintayksikko (:id hallintayksikko)
    :urakka-id       (:id urakka)
    :urakoitsija     (:id urakoitsija)
    :urakkatyyppi    (:arvo urakkatyyppi)
    :nykytilanne?    (= :nykytilanne tila)
    :alue            nakyva-alue
    :alku            (if (= tila :nykytilanne)
                       (t/minus (pvm/nyt)
                                (t/hours nykytilanteen-aikasuodattimen-arvo))
                       (first historiakuvan-aikavali))
    :loppu           (if (= tila :nykytilanne)
                       (pvm/nyt)
                       (second historiakuvan-aikavali))}
   (tk/valitut-suodattimet suodattimet)))

(defonce hakuparametrit
  (reaction
   (kasaa-parametrit @nav/valittu-hallintayksikko @nav/valittu-urakka
                     @nav/valittu-urakoitsija @nav/valittu-urakkatyyppi
                     @valittu-tila @nav/kartalla-nakyva-alue
                     @nykytilanteen-aikasuodattimen-arvo @historiakuvan-aikavali
                     @suodattimet)))

(defn yhdista-tyokonedata [uusi]
  (let [vanhat (:tyokoneet @tilannekuva-kartalla/haetut-asiat)
        uudet (:tyokoneet uusi)
        uudet-idt (into #{} (keys uudet))]
    (assoc uusi :tyokoneet
           (into {}
                 (filter
                  (fn [[id _]]
                    (uudet-idt id))
                  (merge-with
                   (fn [vanha uusi]
                     (let [vanha-reitti (:reitti vanha)]
                       (assoc uusi
                              :reitti (if (= (:sijainti vanha) (:sijainti uusi))
                                             vanha-reitti
                                             (conj
                                              (or vanha-reitti
                                                  [(:sijainti vanha)])
                                              (:sijainti uusi))))))
                   vanhat uudet))))))

(def edellisen-haun-kayttajan-suodattimet
  (atom {:tila                 @valittu-tila
         :aikavali-nykytilanne @nykytilanteen-aikasuodattimen-arvo
         :aikavali-historia    @historiakuvan-aikavali
         :suodattimet          @suodattimet}))

(def tyhjenna-popupit-kun-filtterit-muuttuu
  (run!
   @valittu-tila
   @nykytilanteen-aikasuodattimen-arvo
   @historiakuvan-aikavali
   @suodattimet
   (kartta/poista-popup!)))

(defn kartan-tyypiksi [t avain tyyppi]
  (assoc t avain (map #(assoc % :tyyppi-kartalla tyyppi) (avain t))))

(defn- suodattimet-muuttuneet? []
  (or (not= @valittu-tila (:tila @edellisen-haun-kayttajan-suodattimet))
      (not= @nykytilanteen-aikasuodattimen-arvo
            (:aikavali-nykytilanne @edellisen-haun-kayttajan-suodattimet))
      (not= @historiakuvan-aikavali
            (:aikavali-historia @edellisen-haun-kayttajan-suodattimet))
      (not= @suodattimet
            (:suodattimet @edellisen-haun-kayttajan-suodattimet))))

(defn- julkaise-tyokonedata! [tulos]
  (tapahtumat/julkaise! {:aihe      :uusi-tyokonedata
                         :tyokoneet (vals (:tyokoneet tulos))})
  tulos)

(defn hae-asiat [hakuparametrit]
  (log "Tilannekuva: Hae asiat (" (pr-str @valittu-tila) ") " (pr-str hakuparametrit))
  (go
    ;; Asetetaan kartalle "Päivitetään karttaa" viesti jos haku tapahtui
    ;; käyttäjän vaihdettua suodattimia
    (when (suodattimet-muuttuneet?)
      (reset! edellisen-haun-kayttajan-suodattimet
              {:tila                 @valittu-tila
               :aikavali-nykytilanne @nykytilanteen-aikasuodattimen-arvo
               :aikavali-historia    @historiakuvan-aikavali
               :suodattimet          @suodattimet})
      (kartta/aseta-paivitetaan-karttaa-tila! true))

    (reset! tilannekuva-kartalla/url-hakuparametrit
            (k/url-parametri (dissoc hakuparametrit :alue)))

    (let [tulos (-> (<! (k/post! :hae-tilannekuvaan hakuparametrit))
                    (yhdista-tyokonedata)
                    (julkaise-tyokonedata!))]
      (when @nakymassa?
        (reset! tilannekuva-kartalla/haetut-asiat tulos))
      (kartta/aseta-paivitetaan-karttaa-tila! false))))

(def asioiden-haku
  (reaction<! [hakuparametrit @hakuparametrit
               nakymassa? @nakymassa?]
              {:odota bufferi}
              (when nakymassa?
                (hae-asiat hakuparametrit))))

;; Säilöö funktion jolla pollaus lopetetaan
(defonce lopeta-haku (atom nil))

(defn aloita-periodinen-haku []
  (log "Tilannekuva: Aloitetaan haku")
  (reset! lopeta-haku
          (paivita-periodisesti asioiden-haku
                                (case @valittu-tila
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
