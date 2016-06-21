(ns harja.tiedot.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.fmt :as format]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.asiakas.kommunikaatio :as k]
            [harja.views.kartta :as kartta]
            [harja.tiedot.tilannekuva.tilannekuva-kartalla
             :as tilannekuva-kartalla]
            [harja.atom :refer-macros [reaction<!] :refer [paivita-periodisesti]]
            [harja.pvm :as pvm]
            [cljs-time.core :as t]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.tilannekuva :as tk]
            [reagent.core :as r])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))
(defonce valittu-tila (atom :nykytilanne))

(def
  ^{:doc   "Kuinka pitkä urakan nimi hyväksytään pudotusvalikkoon"
   :const true}
  urakan-nimen-pituus 39)

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

;; Alustetaan aluesuodattimet näin, jotta näkymä ei näytä tyhmältä, kun haetaan tietoja.
;; False, koska muuten suodatinlaatikko tulee valituksi
;; :haku-kaynnissa tarkoittaa, että vetolaatikkoon piirretään spinneri, jos se aukaistaan
(def oletusalueet {"Varsinais-Suomi"             {:haku-kaynnissa false},
                   "Etelä-Pohjanmaa"             {:haku-kaynnissa false},
                   "Pohjois-Savo"                {:haku-kaynnissa false},
                   "Lappi"                       {:haku-kaynnissa false},
                   "Kaakkois-Suomi"              {:haku-kaynnissa false},
                   "Pirkanmaa"                   {:haku-kaynnissa false},
                   "Uusimaa"                     {:haku-kaynnissa false},
                   "Pohjois-Pohjanmaa ja Kainuu" {:haku-kaynnissa false},
                   "Keski-Suomi"                 {:haku-kaynnissa false}})

(def valittu-urakka-tilannekuvaan-tullessa (atom nil))
(def valittu-hallintayksikko-tilannekuvaan-tullessa (atom nil))

;; Kartassa säilötään suodattimien tila, valittu / ei valittu.
(defonce suodattimet
         (atom
           {:yllapito        {tk/paallystys false
                              tk/paikkaus   false}
            :ilmoitukset     {:tyypit {tk/tpp false
                                       tk/tur false
                                       tk/urk false}
                              :tilat  ilmoitusten-tilat-nykytilanteessa}
            :turvallisuus    {tk/turvallisuuspoikkeamat false}
            :laatupoikkeamat {tk/laatupoikkeama-tilaaja     false
                              tk/laatupoikkeama-urakoitsija false
                              tk/laatupoikkeama-konsultti   false}
            :tarkastukset    {tk/tarkastus-tiesto     false
                              tk/tarkastus-talvihoito false
                              tk/tarkastus-soratie    false
                              tk/tarkastus-laatu      false}
            ;; Näiden pitää osua työkoneen enumeihin
            ;; Kelintarkastus ja tiestotarkastus liittyvät tarkastusten tekoon,
            ;; eivät ole "toteumia". Säilytetty kommenteissa, jotta JOS tarkasten
            ;; tekoa halutaan seurana livenä, niin arvot on täällä valmiiksi
            ;; copypastettavissa..
            :talvi           {tk/auraus-ja-sohjonpoisto          false
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
            :kesa            {tk/koneellinen-niitto         false
                              tk/koneellinen-vesakonraivaus false
                              tk/liikennemerkkien-puhdistus false
                              tk/sorateiden-muokkaushoylays false
                              tk/sorateiden-polynsidonta    false
                              tk/sorateiden-tasaus          false
                              tk/sorastus                   false
                              tk/harjaus                    false
                              tk/paallysteiden-paikkaus     false
                              tk/paallysteiden-juotostyot   false
                              tk/siltojen-puhdistus         false
                              tk/l-ja-p-alueiden-puhdistus  false
                              tk/muu                        false}
            :alueet oletusalueet}))

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

(defn kasaa-parametrit [tila nakyva-alue suodattimet]
  (merge
    {:urakat       (apply clojure.set/union (map val (tk/valitut-suodattimet (:alueet suodattimet))))
     :nykytilanne? (= :nykytilanne tila)
     :alue         nakyva-alue}
    (tk/valitut-suodattimet (dissoc suodattimet :alueet))))

(defn aikaparametrilla [parametrit]
  (merge
    parametrit
    {:alku (if (= @valittu-tila :nykytilanne)
             (t/minus (pvm/nyt)
                      (t/hours @nykytilanteen-aikasuodattimen-arvo))
             (first @historiakuvan-aikavali))
     :loppu (if (= @valittu-tila :nykytilanne)
              (pvm/nyt)
              (second @historiakuvan-aikavali))}))

(def hyt-joiden-urakoilla-ei-arvoa
  ;; Uusimmassa reagentissa tulee funktio r/track, jolla tämä
  ;; olisi hoitunut paljon mukavemmin, mutta onnistuu kai tämä näinkin
  (reaction
    (let [funktio (fn [boolean-arvo]
                    (into #{}
                         (keep
                           (fn [[nimi urakat]]
                             (when-not (or (= (contains? urakat :haku-kaynnissa)) (empty? urakat))
                               (when-not (some
                                           (fn [[suodatin valittu?]]
                                             (= valittu? boolean-arvo))
                                           urakat)
                                 nimi)))
                           (:alueet @suodattimet))))]
      {true (funktio true)
       false (funktio false)})))

(defn- valitse-urakka? [urakka-id hallintayksikko]
  (let [valittu-urakka (:id @valittu-urakka-tilannekuvaan-tullessa)
        valittu-hallintayksikko (:id @valittu-hallintayksikko-tilannekuvaan-tullessa)
        hallintayksikot-joista-ei-mitaan-valittu (get @hyt-joiden-urakoilla-ei-arvoa true)
        hallintayksikot-joista-kaikki-valittu (get @hyt-joiden-urakoilla-ei-arvoa false)]
    (cond
      ;; Valitse urakka, jos se kuuluu hallintayksikköön, joista käyttäjä on valinnut
      ;; kaikki urakat
      (hallintayksikot-joista-kaikki-valittu (:nimi hallintayksikko))
      (do
        #_(log (:nimi hallintayksikko) " on hy, joista kaikki on valittu!")
        true)

      ;; Älä ikinä valitse urakkaa, jos se kuuluu hallintayksikköön, josta käyttäjä
      ;; ei ole valinnut yhtään urakkaa (kaiki on false!)
      (hallintayksikot-joista-ei-mitaan-valittu (:nimi hallintayksikko))
      (do
        #_(log (:nimi hallintayksikko) " on hy, joista ei ole mitään valittu!")
        false)

      ;; Jos murupolun kautta oli valittu urakka tilannekuvaan tultaessa,
      ;; tarkasta, onko tämä urakka se
      valittu-urakka
      (do
        #_(log "Murupolun kautta on valittu urakka; onko tämä se urakka? " (= urakka-id valittu-urakka))
        (= urakka-id valittu-urakka))

      ;; Jos murupolun kautta tultaessa oli valittuna hallintayksikkö,
      ;; tarkasta, kuuluuko tämä urakka siihen hallintayksikköön
      valittu-hallintayksikko
      (do
        #_(log "Murupolun kautta on valittu hy; onko tämä se hy? " (= valittu-hallintayksikko (:id hallintayksikko)))
        (= valittu-hallintayksikko (:id hallintayksikko)))

      ;; Sisään tultaessa oli valittuna "koko maa"
      :else
      (do
        #_(log "Koko maa valittu! :)")
        false))))

(defn- hae-aluesuodattimet [tila urakoitsija urakkatyyppi]
  (go (let [tulos (<! (k/post! :hae-urakat-tilannekuvaan (aikaparametrilla
                                                           {:urakoitsija  (:id urakoitsija)
                                                            :urakkatyyppi (:arvo urakkatyyppi)
                                                            :nykytilanne? (= :nykytilanne tila)})))]
        (into {}
              (map
                (fn [aluekokonaisuus]
                  {(get-in aluekokonaisuus [:hallintayksikko :nimi])
                   (into {}
                         (map
                           (fn [{:keys [id nimi alue]}]
                             [(tk/->Aluesuodatin id
                                             (-> nimi
                                                 (clojure.string/replace " " "_")
                                                 (clojure.string/replace "," "_")
                                                 (clojure.string/replace "(" "_")
                                                 (clojure.string/replace ")" "_")
                                                 (keyword))
                                             (format/lyhennetty-urakan-nimi nimi urakan-nimen-pituus)
                                             alue)
                              (valitse-urakka? id (:hallintayksikko aluekokonaisuus))])
                           (:urakat aluekokonaisuus)))})
                tulos)))))

(defn yhdista-aluesuodattimet [vanhat uudet]
  ;; Yhdistetään kaksi mäppiä, joka sisältää mäppiä
  ;; Otetaan pelkästään kentät uudesta mäpistä,
  ;; mutta jos avaimelle löytyy arvo vanhoista tiedoista, käytetään sitä.
  (into {}
        (map
          (fn [[hallintayksikko urakat]]
            [hallintayksikko
             (into {}
                   (map
                     (fn [[suodatin valittu?]]
                       (let [vanha-arvo (get-in vanhat [hallintayksikko suodatin])
                             arvo (if-not (nil? vanha-arvo) vanha-arvo valittu?)]
                         [suodatin arvo]))
                     urakat))])
          uudet)))

(def uudet-aluesuodattimet
  (reaction<! [tila @valittu-tila
               nakymassa? @nakymassa?
               _ @nykytilanteen-aikasuodattimen-arvo
               _ @historiakuvan-aikavali]
              (go (when nakymassa?
                    (let [tulos (<! (hae-aluesuodattimet tila @nav/valittu-urakoitsija @nav/valittu-urakkatyyppi))
                          yhdistetyt (yhdista-aluesuodattimet (:alueet @suodattimet) tulos)]
                      (swap! suodattimet assoc :alueet yhdistetyt)
                      tulos)))))

(run! (tilannekuva-kartalla/aseta-valitut-organisaatiot! (:alueet @suodattimet)))

(defonce hakuparametrit
  (reaction
   (kasaa-parametrit @valittu-tila @nav/kartalla-nakyva-alue @suodattimet)))

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
            (k/url-parametri (aikaparametrilla (dissoc hakuparametrit :alue))))

    (let [tulos (-> (<! (k/post! :hae-tilannekuvaan (aikaparametrilla hakuparametrit)))
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

(defn paivita-ilmoituksen-tiedot [id]
  (log "Haetaan tarkemmat tiedot ilmoitukselle " (pr-str id))
  (go (let [tiedot (<! (k/post! :hae-ilmoituksia-idlla {:id [id]}))
            _ (log "Tulos on: " (pr-str tiedot))
            tiedot (first tiedot)]
        (when tiedot
          (swap! tilannekuva-kartalla/haetut-asiat
                (fn [asiat]
                  (assoc asiat :ilmoitukset
                               (map (fn [ilmoitus]
                                      (if-not (= (:id ilmoitus) id)
                                        ilmoitus
                                        (merge ilmoitus tiedot)))
                                    (:ilmoitukset asiat)))))))))

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
