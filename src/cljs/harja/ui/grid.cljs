(ns harja.ui.grid
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log tarkkaile! logt] :refer-macros [mittaa-aika]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as y]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo vain-luku-atomina]]
            [harja.ui.validointi :as validointi]
            [harja.ui.skeema :as skeema]
            [goog.events :as events]
            [goog.events.EventType :as EventType]

            [cljs.core.async :refer [<! put! chan]]
            [clojure.string :as str]
            [schema.core :as s :include-macros true]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.makrot :refer [fnc]]))

(def gridia-muokataan? (atom false))
(def +rivimaara-jonka-jalkeen-napit-alaskin+ 20)

;; Otsikot
;; Rivi gridin datassa voi olla Otsikko record, jolloin se näytetään väliotsikkona.
;;

(defrecord Otsikko [teksti])

(defn otsikko
  "Luo otsikon annetulla tekstillä."
  [teksti]
  (assert (not (nil? teksti)) "Anna otsikolle teksti.")
  (->Otsikko teksti))


(defn otsikko? [x]
  (instance? Otsikko x))


;; Ohjausprotokolla
;; Grid protokolla määrittelee protokollan, jolla gridin toimintoja voi ohjata
;; ulkopuolelta.

(defprotocol Grid
  "Ohjausprotokolla, jolla gridin muokkaustilaa voidaan kysellä ja manipuloida."

  (lisaa-rivi! [g rivin-tiedot] "Lisää muokkaustilassa uuden rivin.
Annettu rivin-tiedot voi olla tyhjä tai se voi alustaa kenttien arvoja.")

  (hae-muokkaustila [g] "Hakee tämänhetkisen muokkaustilan, joka on mäppi id:stä rivin tietoihin.")


  (hae-virheet [g] "Hakee tämänhetkisen muokkaustilan mukaiset validointivirheet.")

  (hae-varoitukset [g] "Hakee tämänhetkisen muokkaustilan mukaiset validointivaroitukset.")

  (hae-huomautukset [g] "Hakee tämänhetkisen muokkaustilan mukaiset huomautukset.")

  (nollaa-historia! [g] "Nollaa muokkaushistorian, tämä on lähinnä muokkaus-grid versiota varten. Tällä voi kertoa gridille, että data on täysin muuttunut eikä muokkaushistoria ole enää relevantti.")
  ;; PENDING: oisko "jemmaa muokkaushistoria", jolla sen saisi avaimella talteen ja otettua takaisin?
  (hae-viimeisin-muokattu-id [g] "Hakee viimeisimmän muokatun id:n")
  (muokkaa-rivit! [this funktio args] "Muokkaa kaikki taulukon rivit funktion avulla.")

  (vetolaatikko-auki? [this id] "Tarkista onko vetolaatikko auki annetulla rivin id:llä.")

  (avaa-vetolaatikko! [this id] "Avaa vetolaatikko rivin id:llä.")

  (sulje-vetolaatikko! [this id] "sulje vetolaatikko rivin id:llä.")

  (aseta-virhe! [this rivin-id kentta virheteksti] "Asettaa ulkoisesti virheen rivin kentälle")
  (poista-virhe! [this rivin-id kentta] "Poistaa rivin kentän virheen ulkoisesti")

  ;; PENDING: lisää tänne tarvittaessa muita metodeja
  )

(defprotocol GridKahva
  "Sisäinen protokolla, jolle grid asettaa itsensä."
  (aseta-grid [this grid] "Asetaa gridin, jolle ohjauskutsut pitäisi välittää."))

(defn grid-ohjaus
  "Tekee grid ohjausinstanssin, jonka kutsuva puoli voi antaa gridille."
  []
  (let [gridi (atom nil)]
    (reify
      Grid
      (lisaa-rivi! [_ rivin-tiedot]
        (lisaa-rivi! @gridi rivin-tiedot))

      (hae-muokkaustila [_]
        (hae-muokkaustila @gridi))

      (hae-virheet [_]
        (hae-virheet @gridi))

      (hae-varoitukset [_]
        (hae-varoitukset @gridi))

      (hae-huomautukset [_]
        (hae-huomautukset @gridi))

      (nollaa-historia! [_]
        (nollaa-historia! @gridi))

      (hae-viimeisin-muokattu-id [_]
        (hae-viimeisin-muokattu-id @gridi))

      (muokkaa-rivit! [this funktio args]
        (apply muokkaa-rivit! @gridi funktio args))

      (vetolaatikko-auki? [_ id]
        (vetolaatikko-auki? @gridi id))

      (avaa-vetolaatikko! [_ id]
        (avaa-vetolaatikko! @gridi id))

      (sulje-vetolaatikko! [_ id]
        (sulje-vetolaatikko! @gridi id))

      (aseta-virhe! [_ rivin-id kentta virheteksti]
        (aseta-virhe! @gridi rivin-id kentta virheteksti))
      (poista-virhe! [_ rivin-id kentta]
        (poista-virhe! @gridi rivin-id kentta))

      GridKahva
      (aseta-grid [_ grid]
        (reset! gridi grid)))))

(defn avaa-tai-sulje-vetolaatikko!
  "Vaihtaa vetolaatikon tilaa. Avaa vetolaatikon, jos se on suljettu, muuten sulkee sen."
  [g id]
  (if (vetolaatikko-auki? g id)
    (sulje-vetolaatikko! g id)
    (avaa-vetolaatikko! g id)))

(defn tayta-tiedot-alas
  "Täyttää rivin tietoja alaspäin."
  [rivit s lahtorivi tayta-fn]
  (let [tayta-fn (or tayta-fn

                     ;; Oletusfunktio kopioi tiedon sellaisenaan
                     (let [nimi (:nimi s)
                           lahtoarvo ((or (:hae s) nimi) lahtorivi)
                           aseta (or (:aseta s)
                                     (fn [rivi arvo]
                                       (assoc rivi nimi arvo)))]
                       (fn [_ taytettava]
                         (aseta taytettava lahtoarvo))))]
    (loop [uudet-rivit (list)
           alku false
           [rivi & rivit] rivit]
      (if-not rivi
        uudet-rivit
        (if (= lahtorivi rivi)
          (recur (conj uudet-rivit rivi) true rivit)
          (if-not alku
            (recur (conj uudet-rivit rivi) false rivit)
            (recur (conj uudet-rivit
                         (tayta-fn lahtorivi rivi))
                   true
                   rivit)))))))


;; UI-komponentit
;; Itse gridin UI-komponentit

(defn- vetolaatikon-tila [ohjaus vetolaatikot id]
  (let [vetolaatikko? (contains? vetolaatikot id)]
    ^{:key (str "vetolaatikontila" id)}
    [:td.vetolaatikon-tila.klikattava {:on-click (when vetolaatikko?
                                                   #(do (.preventDefault %)
                                                        (.stopPropagation %)
                                                        (avaa-tai-sulje-vetolaatikko! ohjaus id)))}
     (when vetolaatikko?
       (if (vetolaatikko-auki? ohjaus id)
         (ikonit/livicon-chevron-down)
         (ikonit/livicon-chevron-right)))]))

(defn- vetolaatikko-rivi
  "Funktio, joka palauttaa vetolaatikkorivin tai nil. Huom: kutsu tätä funktiona, koska voi palauttaa nil."
  [vetolaatikot vetolaatikot-auki id colspan]
  (when-let [vetolaatikko (get vetolaatikot id)]
    (let [auki (@vetolaatikot-auki id)]
      ^{:key (str "vetolaatikko" id)}
      [:tr.vetolaatikko {:class (if auki "vetolaatikko-auki" "vetolaatikko-kiinni")}
       [:td {:colSpan colspan}
        [:div.vetolaatikko-sisalto
         (when auki
           vetolaatikko)]]])))




(defn- muokkaus-rivi [{:keys [ohjaus id muokkaa! luokka rivin-virheet rivin-varoitukset rivin-huomautukset voi-poistaa? esta-poistaminen?
                              esta-poistaminen-tooltip piilota-toiminnot?
                              fokus aseta-fokus! tulevat-rivit vetolaatikot]} skeema rivi]
  [:tr.muokataan {:class luokka}
   (doall (for [{:keys [nimi hae aseta fmt muokattava? tasaa tyyppi] :as s} skeema]
            (if (= :vetolaatikon-tila tyyppi)
              ^{:key (str "vetolaatikontila" id)}
              [vetolaatikon-tila ohjaus vetolaatikot id]

              (let [s (assoc s :rivi rivi)
                    hae (or hae
                            #(get % nimi))
                    arvo (hae rivi)
                    kentan-virheet (get rivin-virheet nimi)
                    kentan-varoitukset (get rivin-varoitukset nimi)
                    kentan-huomautukset (get rivin-huomautukset nimi)
                    tasaus-luokka (if (= tasaa :oikea) "tasaa-oikealle" "")
                    fokus-id [id nimi]]

                (if (or (nil? muokattava?) (muokattava? rivi))
                  ^{:key (str nimi)}
                  [:td {:class (str "muokattava " tasaus-luokka (cond
                                                                  (not (empty? kentan-virheet)) " sisaltaa-virheen"
                                                                  (not (empty? kentan-varoitukset)) " sisaltaa-varoituksen"
                                                                  (not (empty? kentan-huomautukset)) " sisaltaa-huomautuksen"))}
                   (cond
                     (not (empty? kentan-virheet)) (virheen-ohje kentan-virheet)
                     (not (empty? kentan-varoitukset)) (virheen-ohje kentan-varoitukset :varoitus)
                     (not (empty? kentan-huomautukset)) (virheen-ohje kentan-huomautukset :huomautus))

                   ;; Jos skeema tukee kopiointia, näytetään kopioi alas nappi
                   (when-let [tayta-alas (:tayta-alas? s)]
                     (when (and (= fokus fokus-id)
                                (tayta-alas arvo)

                                ;; Sallitaan täyttö, vain jos tulevia rivejä on ja kaikkien niiden arvot ovat tyhjiä
                                (not (empty? tulevat-rivit))
                                (every? str/blank? (map hae tulevat-rivit)))

                       [:div {:class (if (= :oikea (:tasaa s))
                                       "pull-left"
                                       "pull-right")}
                        [:div {:style {:position "absolute" :display "inline-block"}}
                         [:button {:class (str "nappi-toissijainen nappi-tayta" (when (:kelluta-tayta-nappi s) " kelluta-tayta-nappi"))
                                   :title (:tayta-tooltip s)
                                   :style {:position "absolute"
                                           :left (when (= :oikea (:tasaa s)) 0)
                                           :right (when-not (= :oikea (:tasaa s)) "100%")}
                                   :on-click #(muokkaa-rivit! ohjaus tayta-tiedot-alas [s rivi (:tayta-fn s)])}
                          (ikonit/livicon-arrow-down) " Täytä"]]]))

                   ;;(log "tehdään kenttä " (pr-str fokus-id) ", nykyinen fokus: " (pr-str fokus))
                   [tee-kentta (assoc s
                                 :focus (= fokus fokus-id)
                                 :on-focus #(aseta-fokus! fokus-id)
                                 :pituus-max (:pituus-max s))
                    (r/wrap
                      arvo
                      (fn [uusi]
                        (if aseta
                          (muokkaa! id (fn [rivi]
                                         (aseta rivi uusi)))
                          (muokkaa! id assoc nimi uusi))))]]
                  ^{:key (str nimi)}
                  [:td {:class (str "ei-muokattava " tasaus-luokka)}
                   ((or fmt str) (hae rivi))])))))
   (when-not piilota-toiminnot?
     [:td.toiminnot
      (when (or (nil? voi-poistaa?) (voi-poistaa? rivi))
        (if (and esta-poistaminen? (esta-poistaminen? rivi))
          [:span (ikonit/livicon-trash-disabled (esta-poistaminen-tooltip rivi))]
          [:span.klikattava {:on-click #(do (.preventDefault %)
                                            (muokkaa! id assoc :poistettu true))}
           (ikonit/livicon-trash)]))
      (when-not (empty? rivin-virheet)                      ; true ;-not (empty? rivin-virheet)
        [:span.rivilla-virheita
         (ikonit/livicon-warning-sign)])])])

(defn- naytto-rivi [{:keys [luokka rivi-klikattu rivi-valinta-peruttu ohjaus id
                            vetolaatikot tallenna piilota-toiminnot? valittu-rivi
                            mahdollista-rivin-valinta]} skeema rivi]
  [:tr {:class (str luokka (when (= rivi @valittu-rivi)
                             " rivi-valittu"))
        :on-click #(do
                    (when rivi-klikattu
                      (if (not= @valittu-rivi rivi)
                        (rivi-klikattu rivi)))
                    (when mahdollista-rivin-valinta
                      (if (= @valittu-rivi rivi)
                        (do (reset! valittu-rivi nil)
                            (when rivi-valinta-peruttu
                              (rivi-valinta-peruttu rivi)))
                        (reset! valittu-rivi rivi))))}
   (for [{:keys [nimi hae fmt tasaa tyyppi komponentti nayta-max-merkkia
                 pakota-rivitys? reunus]} skeema]
     (if (= :vetolaatikon-tila tyyppi)
       ^{:key (str "vetolaatikontila" id)}
       [vetolaatikon-tila ohjaus vetolaatikot id]
       ^{:key (str nimi)}
       [:td {:class (y/luokat
                      (y/tasaus-luokka tasaa)
                      (when pakota-rivitys? "grid-pakota-rivitys")
                      (case reunus
                        :ei "grid-reunus-ei"
                        :vasen "grid-reunus-vasen"
                        :oikea "grid-reunus-oikea"
                        nil))}
        (if (= tyyppi :komponentti)
          (komponentti rivi)
          (let [haettu-arvo (if hae
                              (hae rivi)
                              (get rivi nimi))
                arvon-pituus-rajattu (if nayta-max-merkkia
                                       (if (> (count haettu-arvo) nayta-max-merkkia)
                                         (str (subs haettu-arvo 0 nayta-max-merkkia) "...")
                                         haettu-arvo)
                                       haettu-arvo)]
            (if fmt
              (fmt arvon-pituus-rajattu)
              [nayta-arvo skeema (vain-luku-atomina arvon-pituus-rajattu)])))]))
   (when (and (not piilota-toiminnot?)
              tallenna) [:td.toiminnot])])

(def renderoi-rivia-kerralla 100)

(defn grid
  "Taulukko, jossa tietoa voi tarkastella ja muokata. Skeema on vektori joka sisältää taulukon sarakkeet.
  Jokainen skeeman itemi on mappi, jossa seuraavat avaimet:

  :nimi                                 kentän hakufn
  :fmt                                  kentän näyttämis-fn (oletus str). Ottaa argumenttina kentän arvon.
  :hae                                  funktio, jolla voidaan näyttää arvo kentässä. Ottaa argumenttina koko rivin.
  :otsikko                              ihmiselle näytettävä otsikko
  :tunniste                             rivin tunnistava kenttä, oletuksena :id
  :voi-poistaa?                         funktio, joka kertoo, voiko rivin poistaa
  :esta-poistaminen?                    funktio, joka palauttaa true tai false. Jos palauttaa true, roskakori disabloidaan erikseen annetun tooltipin kera.
  :esta-poistaminen-tooltip             funktio, joka palauttaa tooltipin. ks. ylempi.
  :tallennus-ei-mahdollinen-tooltip     Teksti, joka näytetään jos tallennus on disabloitu
  :voi-lisata?                          voiko rivin lisätä (boolean)
  :tyyppi                               kentän tietotyyppi,  #{:string :puhelin :email :pvm}
  :ohjaus                               gridin ohjauskahva, joka on luotu (grid-ohjaus) kutsulla
  :tasaa                                voit antaa :oikea, :keskita jos haluat tasata kentän
                                        oikealle (esim. rahasummat) tai keskelle
  :max-rivimaara                        montako riviä grid suostuu näyttämään ennen \"liikaa rivejä\"-ilmoitusta
  :max-rivimaaran-ylitys-viesti         custom viesti :max-rivimaara -optiolle
  :reunus                               määrittää sarakkeen solujen reunuksen, oletuksena kaikki
                                        :ei       ei kumpaakaan reunusta
                                        :vasen    vain vasemman puolen reunus
                                        :oikea    vain oikean puolen reunus

  Tyypin mukaan voi olla lisäavaimia, jotka määrittelevät tarkemmin kentän validoinnin.

  Optiot on mappi optioita:
  :tallenna                             funktio, jolle kaikki muutokset, poistot ja lisäykset muokkauksen päätyttyä
                                        jos tallenna funktiota ei ole annettu, taulukon muokkausta ei sallita eikä nappia näytetään
                                        jos tallenna arvo on :ei-mahdollinen, näytetään Muokkaa-nappi himmennettynä
  :tallenna-vain-muokatut               boolean jos päällä, tallennetaan vain muokatut. Oletuksena true
  :peruuta                              funktio jota kutsutaan kun käyttäjä klikkaa Peruuta-nappia muokkausmoodissa
  :rivi-klikattu                        funktio jota kutsutaan kun käyttäjä klikkaa riviä näyttömoodissa (parametrinä rivin tiedot)
  :mahdollista-rivin-valinta            jos true, käyttäjä voi valita rivin gridistä. Valittu rivi korostetaan.
  :rivi-valinta-peruttu                 funktio, joka suoritetaan kun valittua riviä klikataan uudelleen eli valinta perutaan
  :muokkaa-footer                       optionaalinen footer komponentti joka muokkaustilassa näytetään, parametrina Grid ohjauskahva
  :muokkaa-aina                         jos true, grid on aina muokkaustilassa, eikä tallenna/peruuta nappeja ole
  :muutos                               jos annettu, kaikista gridin muutoksista tulee kutsu tähän funktioon.
                                        Parametrina Grid ohjauskahva
  :prosessoi-muutos                     funktio, jolla voi prosessoida muutoksenjälkeisen datan, esim. päivittää laskettuja kenttiä.
                                        Parametrina muokkausdata, palauttaa uuden muokkausdatan
  :aloita-muokkaus-fn                   kutsutaan kun muokkaus alkaa. Kutsuva pää voi tällöin esim. muokata datasisällön eriksi muokkausta varten
  :piilota-toiminnot?                   boolean, piilotetaan toiminnot sarake jos true
  :rivin-luokka                         funktio joka palauttaa rivin luokan
  :uusi-rivi                            jos annettu uuden rivin tiedot käsitellään tällä funktiolla
  :vetolaatikot                         {id komponentti} lisäriveistä, jotka näytetään normaalirivien välissä
                                        jos rivin id:llä on avain tässä mäpissä, näytetään arvona oleva komponentti
                                        rivin alla
  :luokat                               Päätason div-elementille annettavat lisäluokat (vectori stringejä)
  :rivi-ennen                           table rivi ennen headeria, sekvenssi mäppejä, joissa avaimet
                                        :teksti (näytettävä teksti) ja :sarakkeita (colspan)


  "
  [{:keys [otsikko tallenna tallenna-vain-muokatut peruuta tyhja tunniste voi-poistaa? voi-lisata? rivi-klikattu esta-poistaminen? esta-poistaminen-tooltip
           muokkaa-footer muokkaa-aina muutos rivin-luokka prosessoi-muutos aloita-muokkaus-fn piilota-toiminnot? rivi-valinta-peruttu
           uusi-rivi vetolaatikot luokat korostustyyli mahdollista-rivin-valinta max-rivimaara max-rivimaaran-ylitys-viesti tallennus-ei-mahdollinen-tooltip] :as opts} skeema tiedot]
  (let [muokatut (atom nil)                                 ;; muokattu datajoukko
        jarjestys (atom nil)                                ;; id:t indekseissä (tai otsikko)
        uusi-id (atom 0)                                    ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])
        virheet (atom {})                                   ;; validointivirheet: (:id rivi) => [virheet]
        varoitukset (atom {})                               ;; validointivaroitukset: (:id rivi) => [varoitukset]
        huomautukset (atom {})
        viime-assoc (atom nil)                              ;; edellisen muokkauksen, jos se oli assoc-in, polku
        viimeisin-muokattu-id (atom nil)
        tallennus-kaynnissa (atom false)
        valittu-rivi (atom nil)
        rivien-maara (atom (count tiedot))
        renderoi-max-rivia (atom renderoi-rivia-kerralla)
        skeema (keep identity skeema)
        tallenna-vain-muokatut (if (nil? tallenna-vain-muokatut)
                                 true
                                 tallenna-vain-muokatut)

        fokus (atom nil)                                    ;; nyt fokusoitu item [id :sarake]

        vetolaatikot-auki (atom (into #{}
                                      (:vetolaatikot-auki opts)))
        validoi-ja-anna-virheet (fn [virheet uudet-tiedot tyyppi]
                                  (into {}
                                        (keep (fn [rivi]
                                                (if (::poistettu rivi)
                                                  nil
                                                  (let [virheet (validointi/validoi-rivi uudet-tiedot rivi skeema tyyppi)]
                                                    (if (empty? virheet)
                                                      nil
                                                      [((or tunniste :id) rivi) virheet]))))
                                              (vals uudet-tiedot))))
        ohjaus (reify Grid
                 (lisaa-rivi! [this rivin-tiedot]
                   (let [id (or ((or tunniste :id) rivin-tiedot) (swap! uusi-id dec))
                         vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         vanhat-varoitukset @varoitukset
                         vanhat-huomautukset @huomautukset
                         vanha-jarjestys @jarjestys
                         uudet-tiedot (swap! muokatut assoc id
                                             ((or uusi-rivi identity)
                                               (merge rivin-tiedot {(or tunniste :id) id :koskematon true})))
                         uusi-jarjestys (swap! jarjestys conj id)]
                     (swap! historia conj [vanhat-tiedot vanhat-virheet vanhat-varoitukset vanhat-huomautukset vanha-jarjestys])
                     (swap! virheet (fn [virheet]
                                      (validoi-ja-anna-virheet virheet uudet-tiedot :validoi)))
                     (swap! varoitukset (fn [varoitukset]
                                          (validoi-ja-anna-virheet varoitukset uudet-tiedot :varoita)))
                     (swap! huomautukset (fn [huomautukset]
                                           (validoi-ja-anna-virheet huomautukset uudet-tiedot :huomauta)))
                     (log "VIRHEET: " (pr-str @virheet))
                     (when muutos
                       (muutos this))))
                 (hae-muokkaustila [_]
                   @muokatut)
                 (hae-virheet [_]
                   @virheet)
                 (hae-varoitukset [_]
                   @varoitukset)
                 (hae-huomautukset [_]
                   @huomautukset)
                 (nollaa-historia! [_]
                   (reset! historia []))
                 (hae-viimeisin-muokattu-id [_]
                   @viimeisin-muokattu-id)

                 (aseta-virhe! [_ rivin-id kentta virheteksti]
                   (swap! virheet assoc-in [rivin-id kentta] [virheteksti]))
                 (poista-virhe! [_ rivin-id kentta]
                   (swap! virheet
                          (fn [virheet]
                            (let [virheet (update-in virheet [rivin-id] dissoc kentta)]
                              (if (empty? (get virheet rivin-id))
                                (dissoc virheet rivin-id)
                                virheet)))))

                 (muokkaa-rivit! [this funktio args]
                   (let [vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         vanhat-varoitukset @varoitukset
                         vanhat-huomautukset @huomautukset
                         vanha-jarjestys @jarjestys
                         uudet-tiedot (swap! muokatut (fn [muokatut]
                                                        (let [muokatut-jarjestyksessa (map (fn [id]
                                                                                             (get muokatut id))
                                                                                           @jarjestys)]
                                                          (into {}
                                                                (map (juxt (or tunniste :id) #(dissoc % :koskematon)))
                                                                (apply funktio muokatut-jarjestyksessa args)))))]
                     (when-not (= vanhat-tiedot uudet-tiedot)
                       (reset! viimeisin-muokattu-id nil)   ;; bulk muutoksesta ei jätetä viimeisintä muokkausta
                       (swap! historia conj [vanhat-tiedot vanhat-virheet vanhat-varoitukset
                                             vanhat-huomautukset vanha-jarjestys])
                       (swap! virheet (fn [virheet]
                                        (validoi-ja-anna-virheet virheet uudet-tiedot :validoi)))
                       (swap! varoitukset (fn [varoitukset]
                                            (validoi-ja-anna-virheet varoitukset uudet-tiedot :varoita)))
                       (swap! huomautukset (fn [huomautukset]
                                             (validoi-ja-anna-virheet huomautukset uudet-tiedot :huomauta))))

                     (when muutos
                       (muutos this))))

                 (vetolaatikko-auki? [_ id]
                   (@vetolaatikot-auki id))

                 (avaa-vetolaatikko! [_ id]
                   (swap! vetolaatikot-auki conj id))

                 (sulje-vetolaatikko! [_ id]
                   (swap! vetolaatikot-auki disj id)))

        ;; Tekee yhden muokkauksen säilyttäen undo historian
        muokkaa! (fn [id funktio & argumentit]
                   (let [vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         vanhat-varoitukset @varoitukset
                         vanhat-huomautukset @huomautukset
                         vanha-jarjestys @jarjestys
                         uudet-tiedot (swap! muokatut
                                             (fn [muokatut]
                                               (let [uusi-data (update-in muokatut [id]
                                                                          (fn [rivi]
                                                                            (apply funktio (dissoc rivi :koskematon) argumentit)))]
                                                 (if prosessoi-muutos
                                                   (prosessoi-muutos uusi-data)
                                                   uusi-data))))]
                     (when-not (= vanhat-tiedot uudet-tiedot)
                       ;;(log "VANHAT: " (pr-str vanhat-tiedot) "\nUUDET: " (pr-str uudet-tiedot))
                       (reset! viimeisin-muokattu-id id)
                       (swap! historia conj [vanhat-tiedot vanhat-virheet vanhat-varoitukset
                                             vanhat-huomautukset vanha-jarjestys])
                       (swap! virheet (fn [virheet]
                                        (let [uusi-rivi (get uudet-tiedot id)
                                              rivin-virheet (when-not (:poistettu uusi-rivi)
                                                              (validointi/validoi-rivi uudet-tiedot uusi-rivi skeema :validoi))]
                                          (if (empty? rivin-virheet)
                                            (dissoc virheet id)
                                            (assoc virheet id rivin-virheet)))))
                       (swap! varoitukset (fn [varoitukset]
                                            (let [uusi-rivi (get uudet-tiedot id)
                                                  rivin-varoitukset (when-not (:poistettu uusi-rivi)
                                                                      (validointi/validoi-rivi uudet-tiedot uusi-rivi skeema :varoita))]
                                              (if (empty? rivin-varoitukset)
                                                (dissoc varoitukset id)
                                                (assoc varoitukset id rivin-varoitukset)))))
                       (swap! huomautukset (fn [huomautukset]
                                             (let [uusi-rivi (get uudet-tiedot id)
                                                   rivin-huomautukset (when-not (:poistettu uusi-rivi)
                                                                        (validointi/validoi-rivi uudet-tiedot uusi-rivi skeema :huomauta))]
                                               (if (empty? rivin-huomautukset)
                                                 (dissoc huomautukset id)
                                                 (assoc huomautukset id rivin-huomautukset))))))
                     (when muutos
                       (muutos ohjaus))))
        ;; Peruu yhden muokkauksen
        peru! (fn []
                (let [[muok virh var huom jarj] (peek @historia)]
                  (reset! muokatut muok)
                  (reset! virheet virh)
                  (reset! varoitukset var)
                  (reset! huomautukset huom)
                  (reset! jarjestys jarj))
                (swap! historia pop)
                (when muutos
                  (muutos ohjaus)))

        nollaa-muokkaustiedot! (fn []
                                 (reset! gridia-muokataan? false)
                                 (reset! virheet {})
                                 (reset! varoitukset {})
                                 (reset! huomautukset {})
                                 (reset! muokatut nil)
                                 (reset! jarjestys nil)
                                 (reset! historia nil)
                                 (reset! viime-assoc nil)
                                 (reset! uusi-id 0)
                                 (when rivi-valinta-peruttu (rivi-valinta-peruttu))
                                 (reset! valittu-rivi nil)
                                 (reset! tallennus-kaynnissa false))
        aloita-muokkaus! (fn [tiedot]
                           (nollaa-muokkaustiedot!)
                           (reset! gridia-muokataan? true)
                           (loop [muok {}
                                  jarj []
                                  [r & rivit] ((or aloita-muokkaus-fn identity) tiedot)]
                             (if-not r
                               (do
                                 (reset! muokatut muok)
                                 (reset! jarjestys jarj))
                               (if (otsikko? r)
                                 (recur muok
                                        (conj jarj r)
                                        rivit)
                                 (let [id ((or tunniste :id) r)]
                                   (recur (assoc muok
                                            id (assoc r :koskematon true))
                                          (conj jarj id)
                                          rivit)))))
                           nil)
        alkuperainen-etaisyys-ylareunaan (atom 0)
        kiinnita-otsikkorivi? (atom false)
        otsikkorivi-sijainti-y (atom 0)
        maarita-rendattavien-rivien-maara (fn [this]
                                            (when (and (pos? (dom/elementin-etaisyys-viewportin-alareunaan (r/dom-node this)))
                                                       (< @renderoi-max-rivia @rivien-maara))
                                              (swap! renderoi-max-rivia + renderoi-rivia-kerralla)))
        kasittele-otsikkorivin-kiinnitys (fn [this]
                                           (if (and (neg? (dom/elementin-etaisyys-viewportin-ylareunaan (r/dom-node this)))
                                                    (pos? (dom/elementin-etaisyys-ylareunaan-alareunasta (r/dom-node this))))
                                             (reset! kiinnita-otsikkorivi? true)
                                             (reset! kiinnita-otsikkorivi? false))
                                           (let [sijainti-y (- (dom/scroll-sijainti-ylareunaan)
                                                               @alkuperainen-etaisyys-ylareunaan
                                                               22) ;; FIXME Selvitä thead korkeus, älä hardcoodaa
                                                 ]
                                             (reset! otsikkorivi-sijainti-y sijainti-y)))
        kasittele-scroll-event (fn [this _]
                                 (maarita-rendattavien-rivien-maara this)
                                 (kasittele-otsikkorivin-kiinnitys this))]

    (when-let [ohj (:ohjaus opts)]
      (aseta-grid ohj ohjaus))

    (when muokkaa-aina
      (aloita-muokkaus! tiedot))

    (komp/luo
      (komp/dom-kuuntelija js/window
                           EventType/SCROLL kasittele-scroll-event)
      {:component-will-receive-props
       (fn [this & [_ _ _ tiedot]]
         ;; jos gridin data vaihtuu, muokkaustila on peruttava, jotta uudet datat tulevat näkyviin
         (nollaa-muokkaustiedot!)
         (when muokkaa-aina
           (aloita-muokkaus! tiedot))
         (reset! rivien-maara (count tiedot))
         (maarita-rendattavien-rivien-maara this))

       :component-did-mount
       (fn [this _]
         (reset! alkuperainen-etaisyys-ylareunaan (dom/elementin-etaisyys-viewportin-ylareunaan (r/dom-node this)))
         (maarita-rendattavien-rivien-maara this))

       :component-will-unmount
       (fn []
         (nollaa-muokkaustiedot!))}
      (fnc [{:keys [otsikko tallenna peruuta voi-poistaa? voi-lisata? rivi-klikattu piilota-toiminnot?
                    muokkaa-footer muokkaa-aina rivin-luokka uusi-rivi tyhja vetolaatikot mahdollista-rivin-valinta rivi-valinta-peruttu
                    korostustyyli max-rivimaara max-rivimaaran-ylitys-viesti] :as opts} skeema alkup-tiedot]
           (let [skeema (skeema/laske-sarakkeiden-leveys (keep identity skeema))
                 colspan (if (or piilota-toiminnot? (nil? tallenna))
                           (count skeema)
                           (inc (count skeema)))
                 muokataan (not (nil? @muokatut))
                 tiedot (if max-rivimaara
                          (take max-rivimaara alkup-tiedot)
                          alkup-tiedot)
                 muokkauspaneeli (fn [nayta-otsikko?]
                                   [:div.panel-heading
                                    (if-not muokataan
                                      [:span.pull-right.muokkaustoiminnot
                                       (when (and tallenna
                                                  (not (nil? tiedot)))
                                         [:div (when (and (= :ei-mahdollinen tallenna)
                                                          tallennus-ei-mahdollinen-tooltip)
                                                 {:title (tallennus-ei-mahdollinen-tooltip)})
                                          [:button.nappi-ensisijainen
                                           {:disabled (or (= :ei-mahdollinen tallenna)
                                                          @gridia-muokataan?)
                                            :on-click #(do (.preventDefault %)
                                                           (aloita-muokkaus! tiedot))}
                                           [:span.grid-muokkaa
                                            [y/ikoni-ja-teksti [ikonit/muokkaa] "Muokkaa"]]]])]
                                      [:span.pull-right.muokkaustoiminnot
                                       [:button.nappi-toissijainen
                                        {:disabled (empty? @historia)
                                         :on-click #(do (.stopPropagation %)
                                                        (.preventDefault %)
                                                        (peru!))}
                                        [y/ikoni-ja-teksti [ikonit/kumoa] " Kumoa"]]

                                       (when-not (= false voi-lisata?)
                                         [:button.nappi-toissijainen.grid-lisaa {:on-click #(do (.preventDefault %)
                                                                                                (lisaa-rivi! ohjaus {}))}
                                          [y/ikoni-ja-teksti [ikonit/livicon-plus] (or (:lisaa-rivi opts) "Lisää rivi")]])


                                       (when-not muokkaa-aina
                                         [:button.nappi-myonteinen.grid-tallenna
                                          {:disabled (or (not (empty? @virheet))
                                                         @tallennus-kaynnissa)
                                           :on-click #(when-not @tallennus-kaynnissa
                                                       (let [kaikki-rivit (mapv second @muokatut)
                                                             tallennettavat
                                                             (if tallenna-vain-muokatut
                                                               (do (log "TALLENNA VAIN MUOKATUT")
                                                                   (filter (fn [rivi] (not (:koskematon rivi))) kaikki-rivit))
                                                               kaikki-rivit)]
                                                         (do (.preventDefault %)
                                                             (reset! tallennus-kaynnissa true)
                                                             (go (if (<! (tallenna tallennettavat)))
                                                                 (nollaa-muokkaustiedot!)))))} ;; kutsu tallenna-fn: määrittele paluuarvo?
                                          [y/ikoni-ja-teksti (ikonit/tallenna) "Tallenna"]])

                                       (when-not muokkaa-aina
                                         [:button.nappi-kielteinen.grid-peru
                                          {:on-click #(do
                                                       (.preventDefault %)
                                                       (nollaa-muokkaustiedot!)
                                                       (when peruuta (peruuta))
                                                       nil)}
                                          [y/ikoni-ja-teksti (ikonit/livicon-ban) "Peruuta"]])])
                                    (when nayta-otsikko? [:h6.panel-title otsikko])])]
             [:div.panel.panel-default.livi-grid {:class (clojure.string/join " " luokat)}
              (muokkauspaneeli true)
              [:div.panel-body
               (if (nil? tiedot)
                 (ajax-loader)
                 [:table.grid
                  [:thead (when @kiinnita-otsikkorivi? {:style {:transform (str "translateY(" @otsikkorivi-sijainti-y "px)")}})
                   (when-let [rivi-ennen (:rivi-ennen opts)]
                     [:tr
                      (for [{:keys [teksti sarakkeita tasaa]} rivi-ennen]
                        ^{:key teksti}
                        [:th {:colSpan (or sarakkeita 1)
                              :class (y/tasaus-luokka tasaa)}
                         teksti])])
                   [:tr
                    (for [{:keys [otsikko leveys nimi otsikkorivi-luokka tasaa]} skeema]
                      ^{:key (str nimi)}
                      [:th {:class (y/luokat otsikkorivi-luokka
                                             (y/tasaus-luokka tasaa))
                            :width (or leveys "5%")} otsikko])
                    (when (and (not piilota-toiminnot?)
                               tallenna)
                      [:th.toiminnot {:width "40px"} " "])]]
                  [:tbody
                   (if muokataan
                     ;; Muokkauskäyttöliittymä
                     (let [muokatut @muokatut
                           jarjestys @jarjestys
                           tulevat-rivit (fn [aloitus-idx]
                                           ;;(log "TULEVAT RIVIT, alk: " (pr-str aloitus-idx))
                                           ;;(log "jarjestys: " (pr-str (drop (inc aloitus-idx) jarjestys)))
                                           (map #(get muokatut %) (drop (inc aloitus-idx) jarjestys)))]
                       (if (empty? muokatut)
                         [:tr.tyhja [:td {:colSpan colspan} tyhja]]
                         (let [kaikki-virheet @virheet
                               kaikki-varoitukset @varoitukset
                               kaikki-huomautukset @huomautukset
                               nykyinen-fokus @fokus]
                           (doall (mapcat #(keep identity %)
                                          (map-indexed
                                            (fn [i id]
                                              (if (otsikko? id)
                                                (let [teksti (:teksti id)]
                                                  [^{:key teksti}
                                                  [:tr.otsikko
                                                   [:td {:colSpan colspan}
                                                    [:h5 teksti]]]])
                                                (let [rivi (get muokatut id)
                                                      rivin-virheet (get kaikki-virheet id)
                                                      rivin-varoitukset (get kaikki-varoitukset id)
                                                      rivin-huomautukset (get kaikki-huomautukset id)]
                                                  (when-not (:poistettu rivi)
                                                    [^{:key id}
                                                    [muokkaus-rivi {:ohjaus ohjaus
                                                                    :vetolaatikot vetolaatikot
                                                                    :muokkaa! muokkaa!
                                                                    :luokka (str (if (even? (+ i 1))
                                                                                   "parillinen"
                                                                                   "pariton"))
                                                                    :id id
                                                                    :rivin-virheet rivin-virheet
                                                                    :rivin-varoitukset rivin-varoitukset
                                                                    :rivin-huomautukset rivin-huomautukset
                                                                    :voi-poistaa? voi-poistaa?
                                                                    :esta-poistaminen? esta-poistaminen?
                                                                    :esta-poistaminen-tooltip esta-poistaminen-tooltip
                                                                    :fokus nykyinen-fokus
                                                                    :aseta-fokus! #(reset! fokus %)
                                                                    :tulevat-rivit (tulevat-rivit i)
                                                                    :piilota-toiminnot? piilota-toiminnot?}
                                                     skeema rivi]
                                                     (vetolaatikko-rivi vetolaatikot vetolaatikot-auki id colspan)]))))
                                            jarjestys))))))

                     ;; Näyttömuoto
                     (let [rivit (take @renderoi-max-rivia tiedot)]
                       (if (empty? rivit)
                         [:tr.tyhja [:td {:col-span colspan} tyhja]]
                         (doall
                           (let [rivit-jarjestetty (sort-by
                                                     (fn [rivi] (if (:yhteenveto rivi) 1 0)) ; Yhteenveto-rivin tulee olla aina viimeisenä
                                                     rivit)]
                             (mapcat #(keep identity %)
                                     (map-indexed
                                       (fn [i rivi]
                                         (if (otsikko? rivi)
                                           [^{:key (:teksti rivi)}
                                           [:tr.otsikko
                                            [:td {:colSpan colspan}
                                             [:h5 (:teksti rivi)]]]]

                                           (let [id ((or tunniste :id) rivi)]
                                             [^{:key id}
                                             [naytto-rivi {:ohjaus ohjaus
                                                           :vetolaatikot vetolaatikot
                                                           :id id
                                                           :tallenna tallenna
                                                           :luokka (str (if (even? (+ i 1)) "parillinen" "pariton")
                                                                        (when rivi-klikattu
                                                                          " klikattava ")
                                                                        (when (:korosta rivi) " korostettu-rivi ")
                                                                        (when (:lihavoi rivi) " bold ")
                                                                        (when (:yhteenveto rivi) " yhteenveto ")
                                                                        (when rivin-luokka
                                                                          (rivin-luokka rivi)))
                                                           :rivi-klikattu rivi-klikattu
                                                           :rivi-valinta-peruttu rivi-valinta-peruttu
                                                           :valittu-rivi valittu-rivi
                                                           :mahdollista-rivin-valinta mahdollista-rivin-valinta
                                                           :piilota-toiminnot? piilota-toiminnot?}
                                              skeema rivi]
                                              (vetolaatikko-rivi vetolaatikot vetolaatikot-auki id (inc (count skeema)))])))
                                       rivit-jarjestetty)))))))]])

               (when (and max-rivimaara (> (count alkup-tiedot) max-rivimaara))
                 [:div.alert-warning (or max-rivimaaran-ylitys-viesti
                                         "Liikaa hakutuloksia, rajaa hakua")])
               (when (and muokataan muokkaa-footer)
                 [muokkaa-footer ohjaus])]
              ;taulukon allekin muokkaustoiminnot jos rivejä yli rajamäärän (joko muokkaus- tai näyttötila)
              (when (> (count (or @muokatut tiedot))
                       +rivimaara-jonka-jalkeen-napit-alaskin+)
                [:span.gridin-napit-alhaalla
                 (muokkauspaneeli false)])])))))


(defn muokkaus-grid
  "Versio gridistä, jossa on vain muokkaustila. Tilan tulee olla muokkauksen vaatimassa {<id> <tiedot>} array mapissa.
  Tiedot tulee olla atomi tai wrapatty data, jota tietojen muokkaus itsessään manipuloi.

Optiot on mappi optioita:
  :muokkaa-footer  optionaalinen footer komponentti joka muokkaustilassa näytetään, parametrina Grid ohjauskahva
  :muutos          jos annettu, kaikista gridin muutoksista tulee kutsu tähän funktioon.
                   Parametrina Grid ohjauskahva
  :uusi-rivi       jos annettu uuden rivin tiedot käsitellään tällä funktiolla
  :voi-muokata?    jos false, tiedot eivät ole muokattavia ollenkaan
  :voi-lisata?     jos false, uusia rivejä ei voi lisätä
  :voi-kumota?     jos false, kumoa-nappia ei näytetä
  :voi-poistaa?    funktio, joka palauttaa true tai false.
  :rivinumerot?    Lisää ylimääräisen sarakkeen, joka listaa rivien numerot alkaen ykkösestä
  :jarjesta        jos annettu funktio, sortataan rivit tämän mukaan
  :piilota-toiminnot? boolean, piilotetaan toiminnot sarake jos true
  :luokat          Päätason div-elementille annettavat lisäkuokat (vectori stringejä)
  :virheet         atomi gridin virheitä {rivinid {:kentta (\"virhekuvaus\")}}, jos ei anneta
                   luodaan sisäisesti atomi virheille
  :uusi-id         seuraavan uuden luotavan rivin id, jos ei anneta luodaan uusia id:tä
                   sarjana -1, -2, -3, ...
  :validoi-aina?   jos true, validoidaan tiedot aina renderissä (ei vain muutoksessa).
                   Tämä on hyödyllinen, jos gridin tieto muuttuu ulkoisesta syystä.
  "
  [{:keys [otsikko tyhja tunniste voi-poistaa? rivi-klikattu rivinumerot? voi-kumota?
           voi-muokata? voi-lisata? jarjesta piilota-toiminnot?
           muokkaa-footer muutos uusi-rivi luokat validoi-aina?] :as opts} skeema muokatut]
  (let [uusi-id (atom 0)                                    ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])
        virheet-atom (or (:virheet opts) (atom {}))         ;; validointivirheet: (:id rivi) => [virheet]
        viime-assoc (atom nil)                              ;; edellisen muokkauksen, jos se oli assoc-in, polku
        vetolaatikot-auki (atom (into #{}
                                      (:vetolaatikot-auki opts)))
        ohjaus-fn (fn [muokatut virheet]
                    (reify Grid
                      (lisaa-rivi! [this rivin-tiedot]
                        (let [id (or (:id rivin-tiedot) (swap! uusi-id dec))
                              vanhat-tiedot @muokatut
                              vanhat-virheet @virheet
                              uudet-tiedot (swap! muokatut assoc id
                                                  ((or uusi-rivi identity)
                                                    (merge rivin-tiedot {:id id :koskematon true})))]
                          (swap! historia conj [vanhat-tiedot vanhat-virheet])
                          (swap! virheet (fn [virheet]
                                           (let [rivin-virheet (validointi/validoi-rivi uudet-tiedot (get uudet-tiedot id) skeema)]
                                             (if (empty? rivin-virheet)
                                               (dissoc virheet id)
                                               (assoc virheet id rivin-virheet)))))
                          (when muutos
                            (muutos this))))
                      (hae-muokkaustila [_]
                        @muokatut)
                      (hae-virheet [_]
                        @virheet)
                      (nollaa-historia! [_]
                        (reset! historia []))

                      (vetolaatikko-auki? [_ id]
                        (@vetolaatikot-auki id))
                      (avaa-vetolaatikko! [_ id]
                        (swap! vetolaatikot-auki conj id))
                      (sulje-vetolaatikko! [_ id]
                        (swap! vetolaatikot-auki disj id))
                      ))

        ;; Tekee yhden muokkauksen säilyttäen undo historian
        muokkaa! (fn [muokatut virheet id funktio & argumentit]
                   (let [vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         uudet-tiedot (swap! muokatut
                                             (fn [muokatut]
                                               (update-in muokatut [id]
                                                          (fn [rivi]
                                                            (apply funktio (dissoc rivi :koskematon) argumentit)))))]

                     (when-not (= vanhat-tiedot uudet-tiedot)
                       (swap! historia conj [vanhat-tiedot vanhat-virheet])
                       (swap! virheet (fn [virheet]
                                        (let [uusi-rivi (get uudet-tiedot id)
                                              rivin-virheet (when-not (:poistettu uusi-rivi)
                                                              (validointi/validoi-rivi uudet-tiedot uusi-rivi skeema))]
                                          (if (empty? rivin-virheet)
                                            (dissoc virheet id)
                                            (assoc virheet id rivin-virheet))))))
                     (when muutos
                       (muutos (ohjaus-fn muokatut virheet)))))


        ;; Peruu yhden muokkauksen
        peru! (fn [muokatut virheet]
                (let [[muok virh] (peek @historia)]
                  (reset! muokatut muok)
                  (reset! virheet virh))
                (swap! historia pop)
                (when muutos
                  (muutos (ohjaus-fn muokatut virheet))))

        ]


    (r/create-class
      {:component-will-receive-props
       (fn [this [_ {:keys [validoi-aina? virheet]} skeema muokatut]]
         (when validoi-aina?
           (let [muokatut @muokatut]
             (reset! (or virheet virheet-atom)
                     (into {}
                           (keep (fn [[id rivi]]
                                   (let [rivin-virheet (when-not (:poistettu rivi)
                                                         (validointi/validoi-rivi muokatut rivi skeema))]
                                     (when-not (empty? rivin-virheet)
                                       [id rivin-virheet]))))
                           muokatut)))))

       :reagent-render
       (fn [{:keys [otsikko tallenna jarjesta voi-poistaa? voi-muokata? voi-lisata? voi-kumota? rivi-klikattu rivinumerot?
                    muokkaa-footer muokkaa-aina uusi-rivi tyhja vetolaatikot uusi-id validoi-aina?] :as opts} skeema muokatut]
         (let [virheet (or (:virheet opts) virheet-atom)
               skeema (skeema/laske-sarakkeiden-leveys skeema)
               colspan (inc (count skeema))
               ohjaus (ohjaus-fn muokatut virheet)
               voi-muokata? (if (nil? voi-muokata?)
                              true
                              voi-muokata?)
               ]
           (when-let [ohj (:ohjaus opts)]
             (aseta-grid ohj ohjaus))

           [:div.panel.panel-default.livi-grid.livi-muokkaus-grid
            {:class (str (str/join " " luokat)
                         (if voi-muokata? " nappeja"))}
            [:div.panel-heading
             (when otsikko [:h6.panel-title otsikko])
             (when (not= false voi-muokata?)
               [:span.pull-right.muokkaustoiminnot
                (when (not= false voi-kumota?)
                  [:button.nappi-toissijainen
                   {:disabled (empty? @historia)
                    :on-click #(do (.stopPropagation %)
                                   (.preventDefault %)
                                   (peru! muokatut virheet))}
                   (ikonit/peru) " Kumoa"])
                (when (not= false voi-lisata?)
                  [:button.nappi-toissijainen.grid-lisaa
                   {:on-click #(do (.preventDefault %)
                                   (lisaa-rivi! ohjaus
                                                (if uusi-id
                                                  {:id uusi-id}
                                                  {})))}
                   (ikonit/livicon-plus) (or (:lisaa-rivi opts) "Lisää rivi")])])]
            [:div.panel-body
             [:table.grid
              [:thead
               [:tr
                (if rivinumerot? [:th {:width "40px"} " "])
                (for [{:keys [otsikko leveys nimi]} skeema]
                  ^{:key (str nimi)}
                  [:th.rivinumero {:width (or leveys "5%")} otsikko])
                (when-not piilota-toiminnot?
                  [:th.toiminnot {:width "40px"} " "])]]

              [:tbody
               (let [muokatut-atom muokatut
                     muokatut @muokatut]
                 (if (every? :poistettu (vals muokatut))
                   [:tr.tyhja [:td {:col-span (inc (count skeema))} tyhja]]
                   (let [kaikki-virheet @virheet]
                     (doall
                       (mapcat
                         #(keep identity %)
                         (map-indexed
                           (fn [i [id rivi]]
                             (let [rivin-virheet (get kaikki-virheet id)]
                               (when-not (:poistettu rivi)
                                 [
                                  ^{:key id}
                                  [:tr.muokataan {:class (str (if (even? (+ i 1))
                                                                "parillinen"
                                                                "pariton"))}
                                   (if rivinumerot? [:td.rivinumero (+ i 1)])
                                   (for [{:keys [nimi hae aseta fmt muokattava? tyyppi] :as s} skeema]
                                     (if (= :vetolaatikon-tila tyyppi)
                                       ^{:key (str "vetolaatikontila" id)}
                                       [vetolaatikon-tila ohjaus vetolaatikot id]
                                       (let [s (assoc s :rivi rivi)
                                             arvo (if hae
                                                    (hae rivi)
                                                    (get rivi nimi))
                                             kentan-virheet (get rivin-virheet nimi)]
                                         (if (or (nil? muokattava?) (muokattava? rivi i))
                                           ^{:key (str nimi)}
                                           [:td {:class (str "muokattava "
                                                             (when-not (empty? kentan-virheet)
                                                               "sisaltaa-virheen"))}
                                            (when-not (empty? kentan-virheet)
                                              (virheen-ohje kentan-virheet))

                                            (if voi-muokata?
                                              [tee-kentta s (r/wrap
                                                              arvo
                                                              (fn [uusi]
                                                                (if aseta
                                                                  (muokkaa! muokatut-atom virheet
                                                                            id (fn [rivi]
                                                                                 (aseta rivi uusi)))
                                                                  (muokkaa! muokatut-atom virheet id assoc nimi uusi))))]
                                              [nayta-arvo s (vain-luku-atomina arvo)])]

                                           ^{:key (str nimi)}
                                           [:td {:class (str "ei-muokattava")}
                                            ((or fmt str) (if hae
                                                            (hae rivi)
                                                            (get rivi nimi)))]))))
                                   (when-not piilota-toiminnot?
                                     [:td.toiminnot
                                      (when (and (not= false voi-muokata?)
                                                 (or (nil? voi-poistaa?) (voi-poistaa? rivi)))
                                        [:span.klikattava {:on-click #(do (.preventDefault %)
                                                                          (muokkaa! muokatut-atom virheet id assoc :poistettu true))}
                                         (ikonit/livicon-trash)])
                                      (when-not (empty? rivin-virheet)
                                        [:span.rivilla-virheita
                                         (ikonit/livicon-warning-sign)])])]

                                  (vetolaatikko-rivi vetolaatikot vetolaatikot-auki id colspan)])))
                           (if jarjesta
                             (sort-by (comp jarjesta second) (seq muokatut))
                             (seq muokatut))))))))]]
             (when (and (not= false voi-muokata?) muokkaa-footer)
               [muokkaa-footer ohjaus])]]))})))

; Apufunktiot

(defn filteroi-uudet-poistetut
  "Ottaa datan muokkausgrid-muodossa (avaimet kokonaislukuja, jotka mappautuvat riveihin) ja palauttaa sellaiset
  rivit, jotka eivät ole uusia ja poistettuja. Paluuarvo on vectori mappeja."
  [rivit]
  (filter
    #(not (and (true? (:poistettu %))
               (neg? (:id %)))) (vals rivit)))

(defn poista-idt
  "Ottaa mapin ja polun. Olettaa, että polun päässä on vector.
  Palauttaa mapin, jossa polussa olevasta vectorista on jokaisesta itemistä poistettu id"
  [lomake polku] (assoc-in lomake polku (mapv
                                          (fn [rivi] (dissoc rivi :id))
                                          (get-in lomake polku))))
