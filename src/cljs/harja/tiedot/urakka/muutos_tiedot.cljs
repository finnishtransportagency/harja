(ns harja.tiedot.urakka.muutos-tiedot
  "Urakan muutosten tiedot."
  (:require [harja.ui.lomake :as lomake]
            [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Hae muutostiedot
(defrecord HaeUrakanMuutostiedot [urakka])
(defrecord HaeUrakanMuutostiedotOnnistui [vastaus])
(defrecord HaeUrakanMuutostiedotEpaonnistui [vastaus])

;; Vaihda hoitokausi
(defrecord HoitokausiVaihdettu [urakka hoitokausi])
(defrecord MuokkaaMuutosta [rivi])
(defrecord TallennaMuutos [muutos])
(defrecord TallennaMuutosEpaonnistui [vastaus])
(defrecord ToggleTaulukonNakyvyys [taulukon-avain])

;; muutostyyppikohtaisia eventtejä
(defrecord MuokkaaLaskettujenMuutoksienSyita [])
(defrecord MuokkaaRahavaraustenMuutoksienSyita [])
(defrecord KopioiPysyvaMuutosTulevilleHoitovuosille [hoitovuosi rivit])

(defrecord HaeMuutoksenTiedot [muutos])
(defrecord HaeMuutoksenTiedotOnnistui [vastaus muutos valittu-hoitokausi])
(defrecord HaeMuutoksenTiedotEpaonnistui [vastaus])

(defrecord TallennaLaskettujenMuutostenSyyt [rivit])
(defrecord TallennaRahavarausmuutostenSyyt [rivit])
(defrecord TallennaRahavarausmuutostenSyytEpaonnistui [vastaus])


;; Liitteet
(defrecord LisaaLiite [liite])
(defrecord PoistaLisattyLiite [])
(defrecord PoistaTallennettuLiite [liite-id])
(defrecord PoistaPoistetutLiitteet [liite-id])
(defrecord PaivitaToimenpiteenTehtavamaarat [taulukon-rivit])
(defrecord PaivitaToimenpiteenTavoitehinnanMuutos [rivi tpi hk-alkuvuosi])

;; aika ennen 2025-2026 hoitovuotta
(defrecord LisaaTavoitehintojenMuutos [])
(defrecord LisaaSuunniteltujenMaarienMuutos [])
;; Päänäkymä ja listaus
(defrecord ValitseUrakka [urakka])
(defrecord NakymastaPoistuttiin [])

(defrecord PaivitaLomake [lomake])

(defn valitse-urakka [app urakka]
  (let [hoitokaudet (u/hoito-tai-sopimuskaudet urakka)
        vanha-hoitokausi (:valittu-hoitokausi app)
        uusi-hoitokausi (if (contains? (set hoitokaudet) vanha-hoitokausi)
                          vanha-hoitokausi
                          (u/paattele-valittu-hoitokausi hoitokaudet))]
    (-> @tila/muutokset
        (assoc :urakan-hoitokaudet hoitokaudet)
        (assoc :valittu-hoitokausi uusi-hoitokausi))))

(def johto-ja-hallintokorvausmuutokset-atom (atom nil))

(defn johto-ja-hallintokorvausmuutoksen-rivit
  "Luo johto-ja-hallintokorvausmuutoksen rivit eli kulut. Yhdistää tyhjät rivit ja kannasta tulevat kulut."
  [valittu-hoitokausi kulut]
  (let [avaimet (pvm/aikavalin-kuukaudet-pvm-vektorina valittu-hoitokausi)
        ;; backend ja frontend pvm:t vähän erimuotoisia. Koska niitä käytetään avaimina, normalisoidaan ensin
        normalisoi #(when (pvm/pvm? %)
                      (pvm/luo-pvm (pvm/vuosi %) (dec (pvm/kuukausi %)) (pvm/paiva %)))
        kulut-normalisoitu (map #(update % :pvm normalisoi) kulut)
        kulut-map (group-by :pvm kulut-normalisoitu)
        normalisoidut-avaimet (map normalisoi avaimet)
        parit (mapcat (fn [pvm]
                        [pvm (or (first (get kulut-map pvm))
                               {:pvm pvm :tavoitehinnan-muutos 0})])
                normalisoidut-avaimet)]
    (apply array-map parit)))

(def pakolliset-kentat-fmt
  {:voimassa_alkaen "Voimassa alkaen"
   :nimi "Nimi"
   :syy "Muutoksen syy"
   :tyyppi "Tyyppi"})

(defn hae-urakan-muutostiedot
  "Hakee urakan muutostiedot, eli miten tavoitehinta ja tehtävä- ja määräluettelo ovat muuttuneet alkuperäisiin tietoihin nähden."
  ([app] (hae-urakan-muutostiedot app (:urakka @tila/yleiset)))
  ([app urakka]
   (tuck-apurit/post! :hae-urakan-muutostiedot
     {:urakka-id (:id urakka)
      :valittu-hoitokausi (:valittu-hoitokausi app)}
     {:onnistui ->HaeUrakanMuutostiedotOnnistui
      :epaonnistui ->HaeUrakanMuutostiedotEpaonnistui})))

(def muutoksien-kayttoonoton-hoitokauden-alkuvuosi 2025)

(defn ennen-muutoksien-kayttoonotto? [valittu-hoitokausi]
  (when valittu-hoitokausi
    (< (pvm/vuosi (first valittu-hoitokausi))
      muutoksien-kayttoonoton-hoitokauden-alkuvuosi)))

(defn alusta-tyyppikohtaisia-arvoja [tyyppi valittu-hoitokausi]
  (case tyyppi
    "johto-ja-hallintokorvaus"
    (reset! johto-ja-hallintokorvausmuutokset-atom
      (johto-ja-hallintokorvausmuutoksen-rivit valittu-hoitokausi []))

    :default))

(defn- poista-liite [app liite-id]
  (let [liitteet (get-in app [:muokattava-muutos :liitteet])]
    (assoc-in app [:muokattava-muutos :liitteet]
      (filter (fn [liite]
                (not= (:id liite) liite-id))
        liitteet))))

(defn pienin-hoitokauden-alkuvuosi-jossa-kirjauksia
  "Hakee toimenpiteiden tiedoista pienimmän hoitovuoden alkukauden jossa kirjauksia"
  [rivit]
  (when (seq rivit)
    (->> rivit
      (mapcat (fn [rivi]
                (concat
                  (map :hoitokauden_alkuvuosi (:tehtavat_ja_maarat rivi))
                  (map :hoitokauden_alkuvuosi (:kustannusvaikutukset rivi))
                  (map :hoitokauden_alkuvuosi (:budjetoidut_summat rivi)))))
      (remove nil?)
      (apply min))))

(extend-protocol tuck/Event
  HoitokausiVaihdettu
  (process-event [{urakka :urakka hoitokausi :hoitokausi} app]
    (let [app (-> app
                  (assoc :valittu-hoitokausi hoitokausi))]
      (hae-urakan-muutostiedot app urakka)
      app))

  HaeUrakanMuutostiedot
  (process-event [{urakka :urakka} app]
    (let [;; Lupauksia voidaan hakea myös välikatselmuksesta, niin tarkistetaan hoitokauden tila sitä ennen
          app (if (:valittu-hoitokausi app)
                app
                (assoc app :valittu-hoitokausi [(pvm/hoitokauden-alkupvm (:hoitokauden-alkuvuosi app))
                                                (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc (:hoitokauden-alkuvuosi app))))]))]
      (do
        (hae-urakan-muutostiedot app urakka)
        app)))

  HaeUrakanMuutostiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      ;; suljetaan aina lomake kun on saatu uudet muutostiedot
      :muokattava-muutos nil
      :tallennus-kesken? false ;; tallennuksen jälkeinen haku tulee tähän handleriin
      :kirjatut-muutokset (:kirjatut-muutokset vastaus)
      :lasketut-muutokset (:lasketut-muutokset vastaus)
      :rahavarausten-muutokset (:rahavarausten-muutokset vastaus)
      :tavoitehinnan-muutokset (:tavoitehinnan-muutokset vastaus)
      :suunniteltujen-maarien-muutokset (:suunniteltujen-maarien-muutokset vastaus)
      :budjettitavoitteet (:budjettitavoitteet vastaus)))

  HaeUrakanMuutostiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Muutostietojen hakeminen epäonnistui!" :varoitus)
    app)

  HaeMuutoksenTiedotOnnistui
  (process-event [{vastaus :vastaus
                   muutos :muutos
                   valittu-hoitokausi :valittu-hoitokausi} app]
    (let [uudet-liitteet (:liitteet vastaus)
          lomakkeen-hoitokausi (get-in app [:muokattava-muutos :hoitovuosi])
          toimenpiteiden-tiedot (:toimenpiteiden-tiedot vastaus)
          toimenpiteiden-tehtavat (:toimenpiteiden-tehtavat vastaus)
          ;; lomakkeen on kyettävä käsittelemään usealle hoitovuodelle tehtäviä kirjauksia. Kun ländätään lomakkeelle,
          ;; halutaan defaulttina näyttää aikaisin hoitovuosi, jossa on kirjauksia. Jos kirjauksia ei ole millekään hoitovuodelle,
          ;; asetetaan oletuksena edelliseltä sivulta ja app statesta "valittu-hoitovuosi"
          ;; jos tästä tulee jossain kohti liian hidas, voidaan tarkastelu suorittaa joko backendissä tai tietokannassakin
          aikaisin-hoitovuosi-jossa-kirjauksia (pienin-hoitokauden-alkuvuosi-jossa-kirjauksia toimenpiteiden-tiedot)
          ;; vain ne hoitovuodet mahdollisia, jotka ovat voimassa alkaen pvm:n jälkeen eli alkupvm on sen jälkeen
          mahdolliset-hoitovuodet-lomakkeella (filter #(pvm/jalkeen? (first %) (get-in app [:muokattava-muutos :voimassa_alkaen]))
                                                (:urakan-hoitokaudet app))
          hoitovuosi-lomakkeelle (or (when aikaisin-hoitovuosi-jossa-kirjauksia
                                       (pvm/vuodesta-hoitokausi aikaisin-hoitovuosi-jossa-kirjauksia))
                                   valittu-hoitokausi)
          app (-> app
                (assoc-in [:muokattava-muutos :liitteet] uudet-liitteet)
                ;; huom: toimenpiteiden tietoja tarvitaan lisäksi  atomissa joka menee muokkausgridille
                ;; on vielä tutkittava, minne kannattaa säilöä muiden kuin lomakkeella valitun hoitokauden tiedot,
                ;; todennäköisesti app-stateen
                (assoc-in [:muokattava-muutos :toimenpiteiden-tiedot] toimenpiteiden-tiedot)
                (assoc-in [:muokattava-muutos :toimenpiteiden-tehtavat] toimenpiteiden-tehtavat)
                ;; alustetaan lomaketta varten hoitokausi samaksi kuin valittu hoitokausi, mutta ne voivat
                ;; erkaantua myöhemmin jos käyttäjä niin haluaa (esim. kirjata pysyvän muutoksen eri hoitokaudelle kuin valittu)
                (assoc-in [:muokattava-muutos :mahdolliset-hoitovuodet-lomakkeella] mahdolliset-hoitovuodet-lomakkeella)
                (assoc-in [:muokattava-muutos :hoitovuosi] hoitovuosi-lomakkeelle))]
      ;; annetaan resetoitua atomiin arvoksi nil, jos ei kuluja ole ko. muutoksessa
      (reset! johto-ja-hallintokorvausmuutokset-atom
        (when (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
          (johto-ja-hallintokorvausmuutoksen-rivit valittu-hoitokausi (:kulut vastaus))))
      app))

  HaeMuutoksenTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Muutoksen tietojen hakeminen epäonnistui!" :varoitus)
    app)

  MuokkaaMuutosta
  (process-event [{rivi :rivi} app]
    (assoc app :muokattava-muutos rivi))

  TallennaMuutos
  (process-event [{muutos :muutos} app]
    (let [urakka (:urakka @tila/yleiset)
          puuttuvat-pakolliset-kentat (map
                                        #(get pakolliset-kentat-fmt %)
                                        (lomake/puuttuvat-pakolliset-kentat muutos))
          muutos (lomake/ilman-lomaketietoja muutos)
          kulut (when (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
                                              ;; luodaan vain kuluja, joiden summa on eri suuri kuin 0 (eli niillä on jotain vaikutusta laskentoihin)
                                              (filter #(and (some? (:tavoitehinnan-muutos %))
                                                         (not= 0 (:tavoitehinnan-muutos %)))
                                                (vals @johto-ja-hallintokorvausmuutokset-atom)))
          muutos (assoc muutos :kulut kulut)]
      (if-not (empty? puuttuvat-pakolliset-kentat)
        (assoc-in app [:muokattava-muutos :puuttuvat-pakolliset-kentat] puuttuvat-pakolliset-kentat)
        (do
          (tuck-apurit/post! :tallenna-muutos
            {:urakka-id (:id urakka)
             :valittu-hoitokausi (:valittu-hoitokausi app)
             :muutos muutos}
            {:onnistui ->HaeUrakanMuutostiedotOnnistui ;; voidaan käyttää samaa eventtiä, koska haetaan uudet muutostiedot tallennuksen jälkeen
             :epaonnistui ->TallennaMuutosEpaonnistui
             :paasta-virhe-lapi? true})
          (assoc app :tallennus-kesken? true)))))

  TallennaMuutosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Muutoksen tallentaminen epäonnistui! "
                           (get-in vastaus [:response :virhe])) :varoitus)
    (assoc app :tallennus-kesken? false))

  TallennaRahavarausmuutostenSyytEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Rahavarauksien muutosten syiden tallentaminen epäonnistui!" :varoitus)
    app)

  ToggleTaulukonNakyvyys
  (process-event [{taulukon-avain :taulukon-avain} app]
    (assoc-in app [:taulukko-nakyvissa? taulukon-avain]
      (not (get-in app [:taulukko-nakyvissa? taulukon-avain]))))

  MuokkaaLaskettujenMuutoksienSyita
  (process-event [_ app]
    ;; TODO: aloita laskettujen muutosten syiden muokkaus taulukossa, ei avata lomaketta
    app)

  TallennaLaskettujenMuutostenSyyt
  (process-event [_ app]
    ;; TODO: Tallenna laskettujen muutosten syyt, mallia vaikka rahavarausmuutosten syiden tallentamisesta
    app)

  MuokkaaRahavaraustenMuutoksienSyita
  (process-event [_ app]
    (assoc app :rahavarausten-syyt-muokattavana? true))

  TallennaRahavarausmuutostenSyyt
  (process-event [{rivit :rivit} app]
    (let [urakka (:urakka @tila/yleiset)]
      (tuck-apurit/post! :tallenna-rahavarausmuutosten-syyt
        {:urakka-id (:id urakka)
         :valittu-hoitokausi (:valittu-hoitokausi app)
         :rivit (map #(select-keys % [:id :syy]) rivit)}
        {:onnistui ->HaeUrakanMuutostiedotOnnistui         ;; voidaan käyttää samaa eventtiä, koska haetaan uudet muutostiedot tallennuksen jälkeen
         :epaonnistui ->TallennaRahavarausmuutostenSyytEpaonnistui})
      app))

  KopioiPysyvaMuutosTulevilleHoitovuosille
  (process-event [{hoitovuosi :hoitovuosi rivit :rivit} app]
    (prn "Tämä on vielä tekemättä")
    ;; TODO: tässä hanskattava muutosten kopiointi tuleville hoitovuosille...
    app)

  HaeMuutoksenTiedot
  (process-event [{muutos :muutos} app]
    (let [valittu-hoitokausi (:valittu-hoitokausi app)]
      (when (:id muutos)
        (tuck-apurit/post! :hae-muutoksen-tiedot
          {:urakka-id @nav/valittu-urakka-id
           :hoitokauden-alkuvuosi (get-in app [:muokattava-muutos :hoitovuosi])
           :muutos {:id (:id muutos)
                    :versio (:versio muutos)
                    :tyyppi (:tyyppi muutos)
                    :liite-idt (into #{}
                                 (map :id (:liitteet muutos)))}}
          {:onnistui ->HaeMuutoksenTiedotOnnistui
           :onnistui-parametrit [muutos valittu-hoitokausi]
           :epaonnistui ->HaeMuutoksenTiedotEpaonnistui}))
      app))


  LisaaLiite
  (process-event
    [{liite :liite} app]
    (prn "LisaaLiite")
    (-> app
      (update-in [:muokattava-muutos :liitteet] conj liite)))

  PoistaPoistetutLiitteet
  (process-event
    [{:keys [liite-id]} app]
    (prn "PoistaPoistetutLiitteet" liite-id)

    (let [liitteet (get-in app [:muokattava-muutos :liitteet])]
      (assoc-in app [:muokattava-muutos :liitteet]
        (filter (fn [liite]
                  (not= (:id liite) liite-id))
          liitteet))))

  PoistaTallennettuLiite
  (process-event
    [{:keys [liite-id]} app]
    (prn "PoistaTallennettuLiite, liite-id: " liite-id)
    (let [{urakka-id :id} @nav/valittu-urakka
          e! (tuck/current-send-function)
          ;; hanskataan tässä myös tilanne, jossa muutosta ei ole vielä tallennettu
          _ (when (get-in app [:muokattava-muutos :id])
              (liitteet/poista-liite-kannasta
                {:urakka-id urakka-id
                 :domain :muutokset
                 :domain-id (get-in app [:muokattava-muutos :id])
                 :liite-id liite-id
                 :poistettu-fn #(e! (->PoistaPoistetutLiitteet liite-id))}))]
      (poista-liite app liite-id)))

  PoistaLisattyLiite
  (process-event [_ app]
    (prn "PoistaLisattyLiite")
    (assoc app :uusi-liite nil))

  PaivitaToimenpiteenTehtavamaarat
  (process-event [{taulukon-rivit :taulukon-rivit} app]
    (prn "PaivitaToimenpiteenTehtavamaarat taulukon-rivit: " taulukon-rivit)
    ;; TODO: päivitä oikeaan kohtaan dataa tavoitehinnan tehtävämäärät mahdollista tallennusta varten
    app)

  PaivitaToimenpiteenTavoitehinnanMuutos
  (process-event [{rivi :rivi
                   tpi :tpi
                   hk-alkuvuosi :hk-alkuvuosi} app]
    (prn "PaivitaToimenpiteenTavoitehinnanMuutos " rivi " tpi " tpi "hk-alkuvuosi " hk-alkuvuosi)
    ;; TODO: päivitä oikeaan kohtaan dataa tavoitehinnan muutos mahdollista tallennusta varten
    app)

  LisaaTavoitehintojenMuutos
  (process-event [_ app]
    app)
  LisaaSuunniteltujenMaarienMuutos
  (process-event [_ app]
    app)

  ValitseUrakka
  (process-event [{urakka :urakka} app]
    (valitse-urakka app urakka))

  NakymastaPoistuttiin
  (process-event [_ app]
    app)

  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (prn "PaivitaLomake: " lomake)
    (assoc app :muokattava-muutos lomake)))
