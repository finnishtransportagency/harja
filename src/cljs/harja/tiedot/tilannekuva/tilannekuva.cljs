(ns harja.tiedot.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [harja.tilanhallinta.tila :as th]
            [cljs.core.async :refer [<! timeout]]
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
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [tuck.core :as tuck])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defonce nakymassa? (atom false))
(defonce valittu-tila (reaction (nav/valittu-valilehti :tilannekuva)))

(defrecord HaeAluesuodattimet [nykytilanne?])
(defrecord TallennaAluesuodattimet [suodattimet])
(defrecord AsetaAluesuodatin [suodatin tila polku])
(defrecord AjaTaustahaku [])
(defrecord LopetaTaustahaku [])

(def
  ^{:doc   "Kuinka pitkä urakan nimi hyväksytään pudotusvalikkoon"
    :const true}
  urakan-nimen-pituus 38)

(def ^{:doc   "Aika joka odotetaan ennen uusien tietojen hakemista, kun
 parametrit muuttuvat"
       :const true}
bufferi 1000)

;; 30s riittää jos näkymä on paikallaan, tiedot haetaan heti uudelleen, jos
;; karttaa siirretään tai zoomataan
(def ^{:doc   "Päivitystiheys tilanenkuvassa, kun parametrit eivät muutu"
       :const true}
hakutiheys-nykytilanne 30000)

(def ^{:doc   "Päivitystiheys historiakuvassa on 20 minuuttia."
       :const true}
hakutiheys-historiakuva 3000)                            ;1200000

(def oletusalueet {})

(def valittu-urakka-tilannekuvaan-tullessa (atom nil))

;; Kartassa säilötään suodattimien tila, valittu / ei valittu.
(defonce suodattimet
         (atom
           {:yllapito          {tk/paallystys                    false
                                tk/paikkaus                      false
                                tk/tietyomaat                    false
                                tk/paaasfalttilevitin            false
                                tk/tiemerkintakone               false
                                tk/kuumennuslaite                false
                                tk/sekoitus-ja-stabilointijyrsin false
                                tk/tma-laite                     false
                                tk/jyra                          false}
            :ilmoitukset       {:tyypit {tk/tpp false
                                         tk/tur false
                                         tk/urk false}}
            :turvallisuus      {tk/turvallisuuspoikkeamat false}
            :tietyoilmoitukset {tk/tietyoilmoitukset false}
            :laatupoikkeamat   {tk/laatupoikkeama-tilaaja     false
                                tk/laatupoikkeama-urakoitsija false
                                tk/laatupoikkeama-konsultti   false}
            :tarkastukset      {tk/tarkastus-tiesto        false
                                tk/tarkastus-talvihoito    false
                                tk/tarkastus-soratie       false
                                tk/tarkastus-laatu         false
                                tk/tilaajan-laadunvalvonta false}
            ;; Näiden pitää osua työkoneen enumeihin
            ;; Kelintarkastus ja tiestotarkastus liittyvät tarkastusten tekoon,
            ;; eivät ole "toteumia". Säilytetty kommenteissa, jotta JOS tarkasten
            ;; tekoa halutaan seurana livenä, niin arvot on täällä valmiiksi
            ;; copypastettavissa..
            :talvi             {tk/auraus-ja-sohjonpoisto          false
                                tk/suolaus                         false
                                tk/pistehiekoitus                  false
                                tk/linjahiekoitus                  false
                                tk/lumivallien-madaltaminen        false
                                tk/sulamisveden-haittojen-torjunta false
                                ;; Liuossuolausta ei ymmärtääkseni enää seurata, mutta kesälomien takia tässä on korjauksen
                                ;; hetkellä pieni informaatiouupelo. Nämä rivit voi poistaa tulevaisuudessa, jos lukija
                                ;; kokee tietävänsä asian varmaksi.
                                ;;tk/liuossuolaus false
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
            :kesa              {tk/koneellinen-niitto                                                                              false
                                tk/koneellinen-vesakonraivaus                                                                      false
                                tk/liikennemerkkien-puhdistus                                                                      false
                                tk/liikennemerkkien-opasteiden-ja-liikenteenohjauslaitteiden-hoito-seka-reunapaalujen-kunnossapito false
                                tk/palteen-poisto                                                                                  false
                                tk/paallystetyn-tien-sorapientareen-taytto                                                         false
                                tk/ojitus                                                                                          false
                                tk/sorapientareen-taytto                                                                           false
                                tk/sorateiden-muokkaushoylays                                                                      false
                                tk/sorateiden-polynsidonta                                                                         false
                                tk/sorateiden-tasaus                                                                               false
                                tk/sorastus                                                                                        false
                                tk/harjaus                                                                                         false
                                tk/paallysteiden-paikkaus                                                                          false
                                tk/paallysteiden-juotostyot                                                                        false
                                tk/siltojen-puhdistus                                                                              false
                                tk/l-ja-p-alueiden-puhdistus                                                                       false
                                tk/muu                                                                                             false}
            :alueet            oletusalueet
            :varustetoteumat   {tk/varustetoteumat false}
            :tieluvat          {tk/tieluvat false}}))

(defn alueita-valittu?
  [suodattimet]
  (let [elyt (vals (:alueet suodattimet))
        urakat (mapcat vals elyt)
        valitut (mapcat vals urakat)]
    (some? (some true? valitut))))

;(defonce paivita-aluevalinta
;         (run! (let [valittuja? (alueita-valittu? @suodattimet)]
;                 (reset! nav/tilannekuvassa-alueita-valittu? valittuja?))))

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

(defn kasaa-parametrit [tila nakyva-alue suodattimet & [alueet]]
  (merge
    {:urakat       (tk/valittujen-suodattimien-idt (or alueet
                                                       (:alueet suodattimet)))
     :nykytilanne? (= :nykytilanne tila)
     :alue         nakyva-alue}
    (tk/valitut-suodattimet (dissoc suodattimet :alueet))))

(defn aikaparametrilla [parametrit]
  (merge
    parametrit
    {:alku  (if (= @valittu-tila :nykytilanne)
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
      {:alku  (first @historiakuvan-aikavali)
       :loppu (second @historiakuvan-aikavali)})))

(defn- hyt-joiden-urakoilla-ei-arvoa* [alueet boolean-arvo]
  (apply merge
         (keep
           (fn [[tyyppi aluekokonaisuudet]]
             {tyyppi (into
                       #{} (keep (fn [[nimi urakat]]
                                   (when-not (empty? urakat)
                                     (when-not (some
                                                 (fn [[suodatin valittu?]]
                                                   (= valittu? boolean-arvo))
                                                 urakat)
                                       nimi)))
                                 aluekokonaisuudet))})
           alueet)))

(def hyt-joiden-urakoilla-ei-arvoa
  ;; Uusimmassa reagentissa tulee funktio r/track, jolla tämä
  ;; olisi hoitunut paljon mukavemmin, mutta onnistuu kai tämä näinkin
  (reaction
    (let [funktio (partial hyt-joiden-urakoilla-ei-arvoa* (:alueet @suodattimet))]
      {true  (funktio true)
       false (funktio false)})))

(defn- valitse-urakka?* [urakka-id hallintayksikko tyyppi valittu-urakka
                         hallintayksikot-joista-ei-mitaan-valittu hallintayksikot-joista-kaikki-valittu]
  (cond

    ;; Jos murupolun kautta oli valittu urakka tilannekuvaan tultaessa,
    ;; tarkasta, onko tämä urakka se
    (= urakka-id valittu-urakka)
    (do
      true)

    ;; Valitse urakka, jos se kuuluu hallintayksikköön, joista käyttäjä on valinnut
    ;; kaikki urakat
    (and
      (get hallintayksikot-joista-kaikki-valittu tyyppi)
      ((get hallintayksikot-joista-kaikki-valittu tyyppi) (:nimi hallintayksikko)))
    (do
      #_(log (:nimi hallintayksikko) " on hy, joista kaikki on valittu!")
      true)

    ;; Älä ikinä valitse urakkaa, jos se kuuluu hallintayksikköön, josta käyttäjä
    ;; ei ole valinnut yhtään urakkaa (kaiki on false!)
    (and
      (get hallintayksikot-joista-ei-mitaan-valittu tyyppi)
      ((get hallintayksikot-joista-ei-mitaan-valittu tyyppi) (:nimi hallintayksikko)))
    (do
      #_(log (:nimi hallintayksikko) " on hy, joista ei ole mitään valittu!")
      false)

    ;; Sisään tultaessa oli valittuna "koko maa"
    :else
    (do
      #_(log "Koko maa valittu! :)")
      false)))

;; Valitaanko palvelimelta palautettu suodatin vai ei.
;; Yhdistäminen tehdään muualla
(defn- valitse-urakka? [urakka-id hallintayksikko tyyppi]
  (valitse-urakka?*
    urakka-id hallintayksikko tyyppi
    (:id @valittu-urakka-tilannekuvaan-tullessa)
    (get @hyt-joiden-urakoilla-ei-arvoa true)
    (get @hyt-joiden-urakoilla-ei-arvoa false)))

(defn aluesuodattimet-nested-mapiksi [tulos]
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
                                                   (valitse-urakka? id hallintayksikko tyyppi)])
                                                urakat))})
                                  aluekokonaisuus))}))
        (group-by :tyyppi tulos)))

(defn- hae-aluesuodattimet [tila urakoitsija]
  (go (let [tulos (<! (k/post! :hae-urakat-tilannekuvaan (aikaparametrilla
                                                           {:urakoitsija  (:id urakoitsija)
                                                            :nykytilanne? (= :nykytilanne tila)})))]
        ;; tulos: [{:tyyppi :x :hallintayksikko {:id . :nimi .} :urakat [{:id :nimi}, ..]} {..}]
        (aluesuodattimet-nested-mapiksi tulos)
        ;; {:x {{:id . :nimi "Lappi"} [{:id 1 :nimi "Kuusamon urakka}]}
        )))

(defn aseta-urakka-valituksi! [id]
  (swap! suodattimet assoc :alueet (tk/suodatin-muutettuna (:alueet @suodattimet) (fn [s val?] [s true]) #{id})))

;; VALINTALOGIIKAN AIKAKIRJA:
;; v1:  Kunnioitetaan aina vanhaa, eli käyttäjän tekemää valintaa. Palvelimelta palautettavat
;;      alueet eivät ikinä ylikirjoita jo olemassa olevia valintoja.
;; v2:  Logiikka muuttui tämä vaatimuksen takia:
;;      Valitse Oulun urakka -> Mene tilannekuvaan -> Ota Oulu pois päältä -> Mene vaikka toteumiin ->
;;      -> Mene takaisin Tilannekuvaan -> Tässä tapauksessa Oulun pitäisi mennä takaisin päälle!
;; v3:  Ei ollut hyvä. Valitse murupolun kautta urakka -> tule tilannekuvaan -> poista urakan valinta
;;      -> vaihda aikaväliä -> Urakka valittiin uudestaan!
;;      Vaihdettiin takaisin versio #1. Jos käyttäjä itse ottaa urakan pois päältä, niin sopii olettaa
;;      että hän ymmärtää sen olevan pois päältä myös takaisin tilannekuvaan tultaessa?
(defn uusi-tai-vanha-suodattimen-arvo [vanha-arvo uusi-arvo]
  (if (some? vanha-arvo) vanha-arvo uusi-arvo))

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
                          {(:elynumero hallintayksikko)
                           (into {}
                                 (map
                                   (fn [[suodatin valittu?]]
                                     (let [vanha-arvo (get-in vanhat [tyyppi (:elynumero hallintayksikko) suodatin])
                                           arvo (uusi-tai-vanha-suodattimen-arvo vanha-arvo valittu?)]
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
  (tilannekuva-kartalla/seuraa-alueita! th/master))

(defn lopeta-alueiden-seuraus! []
  (tilannekuva-kartalla/lopeta-alueen-seuraus! th/master))

;; FIXME: Tämä lasketaan uusiksi joka kerta, kun karttaa siirretään. Isohko homma korjata?
(defonce hakuparametrit
         (reaction
           (kasaa-parametrit @valittu-tila @nav/kartalla-nakyva-alue @suodattimet (:aluesuodattimet @th/master))))

(def edellisen-haun-kayttajan-suodattimet
  (atom {:tila                 @valittu-tila
         :aikavali-nykytilanne @nykytilanteen-aikasuodattimen-arvo
         :aikavali-historia    @historiakuvan-aikavali
         :suodattimet          @suodattimet}))

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
  (let [paikkaukset (yllapitokohteet/yllapitokohteet-kartalle (:paikkaus tulos))]
    (assoc tulos :paikkaus paikkaukset)))

(defn hae-asiat [hakuparametrit]
  (log "Tilannekuva: Hae asiat (" (pr-str @valittu-tila) ") " (pr-str hakuparametrit))
  (when (#{:nykytilanne :historiakuva} @valittu-tila)
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

      ;; Aikaparametri (nykytilanteessa) pitää tietenkin laskea joka haulle uudestaan, jotta
      ;; oikeasti haetaan nykyhetkestä esim. pari tuntia menneisyyteen.
      (reset! tilannekuva-kartalla/url-hakuparametrit
              (k/url-parametri (aikaparametrilla-kuva (dissoc hakuparametrit :alue))))

      ;(let [tulos (-> (<! (k/post! :hae-tilannekuvaan (aikaparametrilla hakuparametrit)))
      ;                (assoc :tarkastukset (:tarkastukset hakuparametrit)))
      ;      tulos (kasittele-tilannekuvan-hakutulos tulos)]
      ;  (log "Hellurei ja hellät tunteet")
      ;  (when @nakymassa?
      ;    (reset! tilannekuva-kartalla/valittu-tila @valittu-tila)
      ;    (reset! tilannekuva-kartalla/haetut-asiat tulos))
        (kartta/aseta-paivitetaan-karttaa-tila! false)
      )))

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
                (log "Nakymassa asioiden haku")
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


(defrecord HaeTilannekuvaan [])
(defrecord AsetaTilannekuvaan [urakat])
(defrecord AsetaValittuTila [valittu-tila])
(defrecord AsetaValittuUrakka [urakka])
(defrecord AsetaHallintayksikko [yksikko])

(defn- taustahaku
  "Hakee taustalla"
  [app haku-fn]
  (let [aja (atom true)]
    (go-loop []
             (<! (timeout 3000))
             (when @aja
               (haku-fn)
               (recur)))
    #(do
       (log "Aja " @aja)
       (reset! aja false))))

(defn- etsi-ja-aseta-urakat-tilaan
  [kaikki & urakat-ja-tilat]
  (loop [[urakka tila] (first (partition 2 (filter #(not (nil? %)) urakat-ja-tilat)))
         loput (rest (partition 2 (filter #(not (nil? %)) urakat-ja-tilat)))
         alueurakat kaikki]
    (let [urakka-id (:id urakka)
          hallintayksikko-id (-> urakka :hallintayksikko :id)
          urakka-avain (some
                          #(when (= (:id %) urakka-id) %)
                          (keys (get alueurakat hallintayksikko-id)))
          alueet (assoc-in alueurakat [(get-in urakka [:hallintayksikko :id]) urakka-avain] tila)]
       (if-not (empty? loput)
       (recur
         (first loput)
         (rest loput)
         alueet)
       alueet))))

(extend-protocol tuck/Event
  AsetaHallintayksikko
  (process-event [{yksikko :yksikko} app]
    (let [app (assoc-in app [:navigaatio :valittu-urakka] nil)]
      (assoc-in app [:navigaatio :valittu-hallintayksikko] yksikko)))
  AsetaValittuUrakka
  (process-event [{urakka :urakka} app]
    (log "Asetetaan urakakk " urakka)
    (let [nykyinen (get-in app [:navigaatio :valittu-urakka])
          polku [:aluesuodattimet :hoito]
          alueet (assoc-in
                   app
                   polku
                   (etsi-ja-aseta-urakat-tilaan
                     (get-in app polku)
                     nykyinen (when-not (nil? nykyinen) false)
                     urakka true))]
      (assoc-in alueet [:navigaatio :valittu-urakka] urakka)))
  AsetaValittuTila
  (process-event [{valittu-tila :valittu-tila} app]
    (assoc-in app [:navigaatio :valittu-tila] valittu-tila))
  HaeTilannekuvaan
  (process-event [_ app]
    (let [async-aseta-fn (tuck/send-async! ->AsetaTilannekuvaan)]
      ; (log "Moikka 2")
      (go
        (reset! tilannekuva-kartalla/url-hakuparametrit
                (k/url-parametri (aikaparametrilla-kuva (dissoc @hakuparametrit :alue))))
        (let [tulos (<!
                        (k/post! :hae-tilannekuvaan
                                 (aikaparametrilla @hakuparametrit)))]
            (async-aseta-fn (kasittele-tilannekuvan-hakutulos (assoc tulos :tarkastukset (:tarkastukset @hakuparametrit))))))
      (assoc-in app [:haku :haku-paalla?] true)))
  AsetaTilannekuvaan
  (process-event [{urakat :urakat} app]
    ;(log "Moikka 3")
    (merge app {:haku        (assoc (:haku app) :haku-paalla? false)
                :tilannekuva urakat}))
  AjaTaustahaku
  (process-event [_ app]
    (log "Ajan taustahaun")
    (let [haku-fn (tuck/send-async! ->HaeTilannekuvaan)]
      (merge app
            {:haku {:lopeta-haku (taustahaku app haku-fn)
                    :taustahaku-paalla? true}})))
  LopetaTaustahaku
  (process-event [_ app]
    (log "Lopetan taustahaun")
    (let [lopeta-haku (get-in app [:haku :lopeta-haku])]
      (merge app {:haku {:lopeta-haku (lopeta-haku)
                         :taustahaku-paalla? false}})))
  AsetaAluesuodatin
  (process-event [{suodatin :suodatin tila :tila polku :polku} app]
    ;(log "Asetetaan " suodatin " polussa " polku " tilaan " tila)
    (assoc-in app (concat [:aluesuodattimet] polku [suodatin]) tila))
  TallennaAluesuodattimet
  (process-event [{suodattimet :suodattimet} app]
    ;(log "Suodattimet!" (->> suodattimet
    ;                         tkuva/aluesuodattimet-nested-mapiksi
    ;                         (tkuva/yhdista-aluesuodattimet (:aluesuodattimet app))))
    (assoc app :fetching false :aluesuodattimet (->> suodattimet
                                                     aluesuodattimet-nested-mapiksi
                                                     (yhdista-aluesuodattimet (:aluesuodattimet app)))))
  HaeAluesuodattimet
  (process-event [{nykytilanne? :nykytilanne?} app]
    ;(log "HaeSuodattimet!" )
    (let [send-fn (tuck/send-async! ->TallennaAluesuodattimet)]
      (go
        (send-fn
          (<!
            (k/post! :hae-urakat-tilannekuvaan
                     (aikaparametrilla {:nykytilanne? nykytilanne?
                                        :urakoitsija  (:id @nav/valittu-urakoitsija)})))))
      (assoc app :fetching true))))