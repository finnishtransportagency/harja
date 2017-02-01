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
            [reagent.core :as r]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))
(defonce valittu-tila (reaction (nav/valittu-valilehti :tilannekuva)))

(def
  ^{:doc "Kuinka pitkä urakan nimi hyväksytään pudotusvalikkoon"
    :const true}
  urakan-nimen-pituus 38)

(def ^{:doc "Aika joka odotetaan ennen uusien tietojen hakemista, kun
 parametrit muuttuvat"
       :const true}
bufferi 1000)

;; 30s riittää jos näkymä on paikallaan, tiedot haetaan heti uudelleen, jos
;; karttaa siirretään tai zoomataan
(def ^{:doc "Päivitystiheys tilanenkuvassa, kun parametrit eivät muutu"
       :const true}
hakutiheys-nykytilanne 30000)

(def ^{:doc "Päivitystiheys historiakuvassa on 20 minuuttia."
       :const true}
hakutiheys-historiakuva 1200000)

(def oletusalueet {})

(def valittu-urakka-tilannekuvaan-tullessa (atom nil))
(def valittu-hallintayksikko-tilannekuvaan-tullessa (atom nil))

;; Kartassa säilötään suodattimien tila, valittu / ei valittu.
(defonce suodattimet
  (atom
    {:yllapito {tk/paallystys false
                tk/paikkaus false
                tk/tietyomaat false
                tk/paaasfalttilevitin false
                tk/tiemerkintakone false
                tk/kuumennuslaite false
                tk/sekoitus-ja-stabilointijyrsin false
                tk/tma-laite false
                tk/jyra false}
     :ilmoitukset {:tyypit {tk/tpp false
                            tk/tur false
                            tk/urk false}}
     :turvallisuus {tk/turvallisuuspoikkeamat false}
     :laatupoikkeamat {tk/laatupoikkeama-tilaaja false
                       tk/laatupoikkeama-urakoitsija false
                       tk/laatupoikkeama-konsultti false}
     :tarkastukset {tk/tarkastus-tiesto false
                    tk/tarkastus-talvihoito false
                    tk/tarkastus-soratie false
                    tk/tarkastus-laatu false}
     ;; Näiden pitää osua työkoneen enumeihin
     ;; Kelintarkastus ja tiestotarkastus liittyvät tarkastusten tekoon,
     ;; eivät ole "toteumia". Säilytetty kommenteissa, jotta JOS tarkasten
     ;; tekoa halutaan seurana livenä, niin arvot on täällä valmiiksi
     ;; copypastettavissa..
     :talvi {tk/auraus-ja-sohjonpoisto false
             tk/suolaus false
             tk/pistehiekoitus false
             tk/linjahiekoitus false
             tk/lumivallien-madaltaminen false
             tk/sulamisveden-haittojen-torjunta false
             ;; Liuossuolausta ei ymmärtääkseni enää seurata, mutta kesälomien takia tässä on korjauksen
             ;; hetkellä pieni informaatiouupelo. Nämä rivit voi poistaa tulevaisuudessa, jos lukija
             ;; kokee tietävänsä asian varmaksi.
             ;;tk/liuossuolaus false
             tk/aurausviitoitus-ja-kinostimet false
             tk/lumensiirto false
             tk/paannejaan-poisto false
             tk/muu false
             ;; Pinnan tasaus on mielestämme kesätoimenpide, mutta Anne
             ;; mailissaan pyysi, että pinnan tasaus tulee myös
             ;; talvitoimenpiteisiin. Maili liittyi suodattimien
             ;; järjestykseen. Pyysin tarkennusta, mutta
             ;; päätin commitoida tämän talteen ettei vaan pääse unohtumaan.
             tk/pinnan-tasaus false}
     :kesa {tk/koneellinen-niitto false
            tk/koneellinen-vesakonraivaus false
            tk/liikennemerkkien-puhdistus false
            tk/liikennemerkkien-opasteiden-ja-liikenteenohjauslaitteiden-hoito-seka-reunapaalujen-kunnossapito false
            tk/palteen-poisto false
            tk/paallystetyn-tien-sorapientareen-taytto false
            tk/ojitus false
            tk/sorapientareen-taytto false
            tk/sorateiden-muokkaushoylays false
            tk/sorateiden-polynsidonta false
            tk/sorateiden-tasaus false
            tk/sorastus false
            tk/harjaus false
            tk/paallysteiden-paikkaus false
            tk/paallysteiden-juotostyot false
            tk/siltojen-puhdistus false
            tk/l-ja-p-alueiden-puhdistus false
            tk/muu false}
     :alueet oletusalueet}))

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
    {:urakat       (tk/valittujen-suodattimien-idt (:alueet suodattimet))
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

(defn aikaparametrilla-kuva
  "Aikaparametri kuvatasolle: nykytilassa ei anneta aikoja, vaan aikavalinta.
  Koska muuten kuvatason parametrit muuttuvat koko ajan ja karttataso vilkkuu koko ajan."
  [parametrit]
  (merge
   parametrit
   (if (= @valittu-tila :nykytilanne)
     {:aikavalinta @nykytilanteen-aikasuodattimen-arvo}
     {:alku (first @historiakuvan-aikavali)
      :loppu (second @historiakuvan-aikavali)})))

(def hyt-joiden-urakoilla-ei-arvoa
  ;; Uusimmassa reagentissa tulee funktio r/track, jolla tämä
  ;; olisi hoitunut paljon mukavemmin, mutta onnistuu kai tämä näinkin
  (reaction
    (let [funktio (fn [boolean-arvo]
                    (into #{}
                          (keep
                            (fn [[tyyppi aluekokonaisuudet]]
                              (map (fn [[nimi urakat]]
                                     (when-not (empty? urakat)
                                       (when-not (some
                                                   (fn [[suodatin valittu?]]
                                                     (= valittu? boolean-arvo))
                                                   urakat)
                                         nimi)))
                                   aluekokonaisuudet))
                            (:alueet @suodattimet))))]
      {true  (funktio true)
       false (funktio false)})))

;; Valitaanko palvelimelta palautettu suodatin vai ei.
;; Yhdistäminen tehdään muualla
(defn- valitse-urakka? [urakka-id hallintayksikko]
  (let [valittu-urakka (:id @valittu-urakka-tilannekuvaan-tullessa)
        valittu-hallintayksikko (:id @valittu-hallintayksikko-tilannekuvaan-tullessa)
        hallintayksikot-joista-ei-mitaan-valittu (get @hyt-joiden-urakoilla-ei-arvoa true)
        hallintayksikot-joista-kaikki-valittu (get @hyt-joiden-urakoilla-ei-arvoa false)]
    (cond

      ;; Jos murupolun kautta oli valittu urakka tilannekuvaan tultaessa,
      ;; tarkasta, onko tämä urakka se
      (= urakka-id valittu-urakka)
      (do
        true)

      ;; Jos murupolun kautta tultaessa oli valittuna hallintayksikkö,
      ;; tarkasta, kuuluuko tämä urakka siihen hallintayksikköön
      (and (nil? valittu-urakka) (= valittu-hallintayksikko (:id hallintayksikko)))
      (do
        true)

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

      ;; Sisään tultaessa oli valittuna "koko maa"
      :else
      (do
        #_(log "Koko maa valittu! :)")
        false))))

(defn- hae-aluesuodattimet [tila urakoitsija]
  (go (let [tulos (<! (k/post! :hae-urakat-tilannekuvaan (aikaparametrilla
                                                           {:urakoitsija  (:id urakoitsija)
                                                            :nykytilanne? (= :nykytilanne tila)})))]
        ;; tulos: [{:tyyppi :x :hallintayksikko {:id . :nimi .} :urakat [{:id :nimi}, ..]} {..}]
        (into {}
              (map (fn [[tyyppi aluekokonaisuus]]
                     {tyyppi (into {}
                                   (map (fn [{:keys [hallintayksikko urakat]}]
                                          {hallintayksikko
                                           (into {}
                                                 (map (fn [{:keys [id nimi alue]}]
                                                        [(tk/->Aluesuodatin id
                                                                            (-> nimi
                                                                                (clojure.string/replace " " "_")
                                                                                (clojure.string/replace "," "_")
                                                                                (clojure.string/replace "(" "_")
                                                                                (clojure.string/replace ")" "_")
                                                                                (keyword))
                                                                            (format/lyhennetty-urakan-nimi urakan-nimen-pituus nimi)
                                                                            alue)
                                                         (valitse-urakka? id hallintayksikko)])
                                                      urakat))})
                                        aluekokonaisuus))}))
              (group-by :tyyppi tulos))
        ;; {:x {{:id . :nimi "Lappi"} [{:id 1 :nimi "Kuusamon urakka}]}
        )))


;; Alkuperäinen logiikka nojasi siihen, että valitaan AINA vanhan suodattimen arvo,
;; jos sellainen löytyy. Jos ei löydy, niin sitten käytetään uuden suodattimen arvoa, jonka
;; valintalogiikka löytyy valitse-urakka? funktiosta.
;; Tämä funktio piti lisätä, koska tietyissä tapauksissa halutaan ylikirjoittaa vanha
;; suodattimen arvo uudella.
;; Esim: Valitse Oulun urakka -> Mene tilannekuvaan -> Ota Oulu pois päältä -> Mene vaikka toteumiin ->
;; -> Mene takaisin Tilannekuvaan -> Tässä tapauksessa Oulun pitäisi mennä takaisin päälle!
(defn uusi-tai-vanha-suodattimen-arvo [vanha-arvo uusi-arvo urakka hallintayksikko]
  (let [arvo (cond
               (nil? vanha-arvo) uusi-arvo
               (= (:id urakka) (:id @valittu-urakka-tilannekuvaan-tullessa)) uusi-arvo
               (and (nil? @valittu-urakka-tilannekuvaan-tullessa)
                    (= (:id hallintayksikko) (:id @valittu-hallintayksikko-tilannekuvaan-tullessa))) uusi-arvo
               :else vanha-arvo)]
    #_(log "Urakalle " (pr-str (:nimi urakka)) " käytetään arvoa " (pr-str arvo) "(" (pr-str vanha-arvo) " => " (pr-str uusi-arvo) ")")
    arvo))

(defn yhdista-aluesuodattimet [vanhat uudet]
  ;; Yhdistetään kaksi mäppiä, joka sisältää mäppiä
  ;; Otetaan pelkästään kentät uudesta mäpistä,
  ;; mutta jos avaimelle löytyy arvo vanhoista tiedoista, käytetään sitä.
  (into {}
        (map
          (fn [[tyyppi aluekokonaisuudet]]
            {tyyppi
             (into {}
                   (map (fn [[hallintayksikko urakat]]
                          {(:nimi hallintayksikko)
                           (into {}
                                 (map
                                   (fn [[suodatin valittu?]]
                                     (let [vanha-arvo (get-in vanhat [tyyppi (:nimi hallintayksikko) suodatin])
                                           arvo (uusi-tai-vanha-suodattimen-arvo vanha-arvo valittu?
                                                                                 suodatin hallintayksikko)]
                                       [suodatin arvo]))
                                   urakat))})
                        aluekokonaisuudet))})
          uudet)))

(def uudet-aluesuodattimet
  (reaction<! [tila @valittu-tila
               nakymassa? @nakymassa?
               _ @nykytilanteen-aikasuodattimen-arvo
               _ @historiakuvan-aikavali]
              (go (when nakymassa?
                    (let [tulos (<! (hae-aluesuodattimet tila @nav/valittu-urakoitsija))
                          yhdistetyt (yhdista-aluesuodattimet (:alueet @suodattimet) tulos)]
                      (swap! suodattimet assoc :alueet yhdistetyt)
                      tulos)))))

(defn seuraa-alueita! []
  (tilannekuva-kartalla/seuraa-alueita! suodattimet))

(defn lopeta-alueiden-seuraus! []
  (tilannekuva-kartalla/lopeta-alueen-seuraus! suodattimet))

;; FIXME: Tämä lasketaan uusiksi joka kerta, kun karttaa siirretään. Isohko homma korjata?
(defonce hakuparametrit
  (reaction
    (kasaa-parametrit @valittu-tila @nav/kartalla-nakyva-alue @suodattimet)))

(def edellisen-haun-kayttajan-suodattimet
  (atom {:tila @valittu-tila
         :aikavali-nykytilanne @nykytilanteen-aikasuodattimen-arvo
         :aikavali-historia @historiakuvan-aikavali
         :suodattimet @suodattimet}))

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

(defn- kasittele-tilannekuvan-hakutulos [tulos]
  (let [paallystykset (yllapitokohteet/yllapitokohteet-kartalle (:paallystys tulos))
        paikkaukset (yllapitokohteet/yllapitokohteet-kartalle (:paikkaus tulos))]
  (assoc tulos :paallystys paallystykset
               :paikkaus paikkaukset)))

(defn hae-asiat [hakuparametrit]
  (log "Tilannekuva: Hae asiat (" (pr-str @valittu-tila) ") " (pr-str hakuparametrit))
  (go
    ;; Asetetaan kartalle "Päivitetään karttaa" viesti jos haku tapahtui
    ;; käyttäjän vaihdettua suodattimia
    (when (suodattimet-muuttuneet?)
      (reset! edellisen-haun-kayttajan-suodattimet
              {:tila @valittu-tila
               :aikavali-nykytilanne @nykytilanteen-aikasuodattimen-arvo
               :aikavali-historia @historiakuvan-aikavali
               :suodattimet @suodattimet})
      (kartta/aseta-paivitetaan-karttaa-tila! true))

    ;; Aikaparametri (nykytilanteessa) pitää tietenkin laskea joka haulle uudestaan, jotta
    ;; oikeasti haetaan nykyhetkestä esim. pari tuntia menneisyyteen.
    (reset! tilannekuva-kartalla/url-hakuparametrit
            (k/url-parametri (aikaparametrilla-kuva (dissoc hakuparametrit :alue))))

    (let [tulos (-> (<! (k/post! :hae-tilannekuvaan (aikaparametrilla hakuparametrit)))
                    (assoc :tarkastukset (:tarkastukset hakuparametrit)))
          tulos (kasittele-tilannekuvan-hakutulos tulos)]
      (when @nakymassa?
        (reset! tilannekuva-kartalla/valittu-tila @valittu-tila)
        (reset! tilannekuva-kartalla/haetut-asiat tulos))
      (kartta/aseta-paivitetaan-karttaa-tila! false))))

(def asioiden-haku
  (reaction<! [hakuparametrit @hakuparametrit
               nakymassa? @nakymassa?
               ;; Uusi haku myös kun aikasuodattimien arvot muuttuvat
               _ @nykytilanteen-aikasuodattimen-arvo
               _ @historiakuvan-aikavali]
               ;; Kun vaihdetaan nykytilanteen ja historiakuvan välillä, haetaan uudet,
               ;; aikasuodattimeen ja tilaan sopivat urakat. Kun tämä haku on valmis,
               ;; lähdetään hakemaan kartalle piirrettävät jutut. Tämän takia emme halua tehdä
               ;; asioiden hakua tilaan sidottuna!
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
                                  :historiakuva hakutiheys-historiakuva
                                  :tienakyma hakutiheys-historiakuva))))

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
