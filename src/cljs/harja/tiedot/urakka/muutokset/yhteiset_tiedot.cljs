(ns harja.tiedot.urakka.muutokset.yhteiset-tiedot
  "Urakan muutosten tiedot - yhteiset."
  (:require [tuck.core :as tuck]
            [clojure.string :as str]
            [reagent.core :refer [atom]]

            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.ui.lomake :as lomake]
            [harja.ui.viesti :as viesti]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]))


(defonce ^{:private true}
  nollatut-valinnat {:haku-kaynnissa? false
                     :muutoksen-tiedot-haku-kaynnissa? false
                     :tallennus-kesken? false
                     :voi-tallentaa? false
                     :lomakkeella-virheita? false
                     :tallenna-painettu? false
                     :muokattava-muutos nil
                     :tavoitehinnan-muutokset nil
                     :suunniteltujen-maarien-muutokset nil
                     :budjettitavoitteet nil
                     :taulukko-nakyvissa? {:kirjatut-muutokset true
                                           :lasketut-muutokset false
                                           :rahavarausten-muutokset false
                                           :tavoitehinnan-muutokset false
                                           :suunniteltujen-maarien-muutokset false}})

(def pakolliset-kentat-fmt {:nimi "Nimi"
                            :tyyppi "Tyyppi"
                            :syy "Muutoksen syy"
                            :voimassa_alkaen "Voimassa alkaen"})

(def +indeksikorjausta-ei-vahvistettu-txt+ "Ei saatavilla")
(def +muutosten-vaikutus-yhteensa-ei-saatavilla+ "Ei saatavilla")
(def muutoksien-kayttoonoton-hoitokauden-alkuvuosi 2025)

(defonce nakymassa? (atom false))

(defn nayta-muutokset-sivu? []
  (boolean
    (and
      @u/valittu-aikavali
      (>= (-> @u/valittu-aikavali first (pvm/vuosi)) 2025))))

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

(defn ennen-muutoksien-kayttoonotto?
  "Aika ennen muutoksien käyttöönottohoitovuotta"
  [valittu-hoitokausi]
  (when valittu-hoitokausi
    (< (pvm/vuosi (first valittu-hoitokausi))
      muutoksien-kayttoonoton-hoitokauden-alkuvuosi)))

(defn hoitovuoden-indeksikorjaus-vahvistettu?
  [{:keys [tavoitehinta-indeksikorjattu-per-hoitovuosi] :as budjettitavoitteet} hoitovuosi]
  (let [hoitokauden-alkuvuosi (some-> hoitovuosi (first) (pvm/vuosi))
        indeksikorjaus-vahvistettu? (get tavoitehinta-indeksikorjattu-per-hoitovuosi hoitokauden-alkuvuosi false)]
    indeksikorjaus-vahvistettu?))


;; --- Tuck-eventit ja käsittelijät ---
;; Hae muutostiedot
(defrecord HaeUrakanMuutostiedot [tyyppi])
(defrecord HaeUrakanMuutostiedotOnnistui [vastaus])
(defrecord HaeUrakanMuutostiedotEpaonnistui [vastaus])

;; Päänäkymä ja listaus
(defrecord ToggleTaulukonNakyvyys [taulukon-avain])
(defrecord PaivitaLomake [lomake])

(defrecord MuokkaaMuutosta [rivi])
(defrecord MuokkaaJohtoJaHallintoMuutosta [rivi])
(defrecord TallennaMuutos [muutos])
(defrecord TallennaMuutosOnnistui [vastaus])
(defrecord TallennaMuutosEpaonnistui [vastaus])

(defrecord HaeMuutoksenTiedot [muutos])
(defrecord HaeMuutoksenTiedotOnnistui [vastaus muutos valittu-hoitokausi])
(defrecord HaeMuutoksenTiedotEpaonnistui [vastaus])

;; Liitteet
(defrecord LisaaLiite [liite])
(defrecord PoistaLisattyLiite [])
(defrecord PoistaTallennettuLiite [liite-id])
(defrecord PoistaPoistetutLiitteet [liite-id])

;; -- Aika ennen 2025-2026 hoitovuotta
(defrecord LisaaTavoitehintojenMuutos [])
(defrecord LisaaSuunniteltujenMaarienMuutos [])

;; -- Siirtymät ja muut UI-toiminnot --
;; - Siirtymä esimerkiksi kustannussuunnitelmasta muutoksiin suoraan lomakkeelle
(defrecord SiirryMuutosNakymaan [])
(defrecord SiirryPysyvanMuutoksenMuokkauslomakkeelle [muutos])

(defn scrollaa-viimeksi-valitulle-riville []
  (.setTimeout js/window (fn [] (siirrin/kohde-elementti-luokka "viimeksi-valittu-tausta")) 150))

(defn hae-urakan-muutostiedot
  "Hakee urakan muutostiedot, eli miten tavoitehinta ja tehtävä- ja määräluettelo ovat muuttuneet alkuperäisiin tietoihin nähden."
  [app]
  (tuck-apurit/post! app :hae-urakan-muutostiedot
    {:urakka-id (-> @tila/yleiset :urakka :id)
     :hoitokaudet @u/valitun-urakan-hoitokaudet
     :valittu-hoitokausi (:valittu-hoitokausi app)
     :laskenta-automatiikka? (:laskenta-automatiikka? app)}
    {:onnistui ->HaeUrakanMuutostiedotOnnistui
     :epaonnistui ->HaeUrakanMuutostiedotEpaonnistui}))

(defn- vastaus-haku-onnistui [app vastaus]
  (assoc app
    :haku-kaynnissa? false
    :kirjatut-muutokset (:kirjatut-muutokset vastaus)
    :aiempien-hoitovuosien-pysyvat-muutokset (:aiempien-hoitovuosien-pysyvat-muutokset vastaus)
    :tehtava-maaramuutokset (:lasketut-muutokset vastaus)
    :rahavarausten-muutokset (:rahavarausten-muutokset vastaus)
    :tavoitehinnan-muutokset (:tavoitehinnan-muutokset vastaus)
    :suunniteltujen-maarien-muutokset (:suunniteltujen-maarien-muutokset vastaus)
    :budjettitavoitteet (:budjettitavoitteet vastaus)))


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

(defn- poista-liite [app liite-id]
  (let [liitteet (get-in app [:muokattava-muutos :liitteet])]
    (assoc-in app [:muokattava-muutos :liitteet]
      (filter (fn [liite] (not= (:id liite) liite-id))
        liitteet))))


(defn- muutos-ilman-ui-tietoja [muutos]
  (dissoc muutos
    ;; Valittu hoitovuosi UI:ssa
    :hoitovuosi
    :mahdolliset-hoitovuodet-lomakkeella
    ;; Pysyvien muutosten aputietoja
    :toimenpiteiden-tehtavat))

(extend-protocol tuck/Event
  HaeUrakanMuutostiedot
  (process-event [{:keys [tyyppi]} app]
    "Tyyppi on joko nil, tai avain :taulukko-nakyvissa? mapille, esim. :lasketut-muutokset
    jos tyyppi annetaan, tämän osion väkänen pysyy auki tallennuksen läpi."
    (let [urakan-alkuvuosi (some->> @u/valitun-urakan-hoitokaudet first first pvm/vuosi)
          laskenta-automatiikka? (boolean (>= urakan-alkuvuosi 2025))]
      (hae-urakan-muutostiedot
        (as-> (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat) app
          (assoc app
            :haku-kaynnissa? true
            :laskenta-automatiikka? laskenta-automatiikka?
            :valittu-hoitokausi @u/valittu-hoitokausi
            :urakan-hoitokaudet @u/valitun-urakan-hoitokaudet)

          (if tyyppi
            ;; Tyyppi passattiin, pidä tämä väkänen auki
            ;; Vähennetään sitä, että sivu ei pompi sinne tänne kun käyttäjä painaa tallenna. 
            (assoc-in app [:taulukko-nakyvissa? tyyppi] true)
            ;; Ei passattu tyyppiä, (esim kun näkymä avataan) -> cleanaa näkymä
            (assoc app
              :kirjatut-muutokset nil
              :tehtava-maaramuutokset nil
              :rahavarausten-muutokset nil
              :aiempien-hoitovuosien-pysyvat-muutokset nil))))))

  HaeUrakanMuutostiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (vastaus-haku-onnistui app vastaus))

  HaeUrakanMuutostiedotEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Muutostietojen hakeminen epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  ToggleTaulukonNakyvyys
  (process-event [{taulukon-avain :taulukon-avain} app]
    (assoc-in app [:taulukko-nakyvissa? taulukon-avain]
      (not (get-in app [:taulukko-nakyvissa? taulukon-avain]))))

  ;; Hakee olemassaolevan muutoksen kaikki tiedot muokkausta varten
  HaeMuutoksenTiedot
  (process-event [{:keys [muutos]}
                  {:keys [valittu-hoitokausi] :as app}]
    (if (:id muutos)
      (do
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
           :epaonnistui ->HaeMuutoksenTiedotEpaonnistui})
        (assoc app :muutoksen-tiedot-haku-kaynnissa? true))
      app))

  HaeMuutoksenTiedotOnnistui
  (process-event [{vastaus :vastaus
                   muutos :muutos
                   valittu-hoitokausi :valittu-hoitokausi} app]
    (let [uudet-liitteet (:liitteet vastaus)
          lomakkeen-hoitokausi (get-in app [:muokattava-muutos :hoitovuosi])
          toimenpiteiden-tiedot (:toimenpiteiden-tiedot vastaus)
          toimenpiteiden-tehtavat (:toimenpiteiden-tehtavat vastaus)
          ;; Lomakkeen on kyettävä käsittelemään usealle hoitovuodelle tehtäviä kirjauksia. Kun ländätään lomakkeelle,
          ;; halutaan defaulttina näyttää aikaisin hoitovuosi, jossa on kirjauksia.
          ;; Jos tästä tulee jossain kohti liian hidas, voidaan tarkastelu suorittaa joko backendissä tai tietokannassakin
          aikaisin-hoitovuosi-jossa-kirjauksia (pienin-hoitokauden-alkuvuosi-jossa-kirjauksia toimenpiteiden-tiedot)
          mahdolliset-hoitovuodet-lomakkeella (:urakan-hoitokaudet app)
          hoitovuosi-lomakkeelle (or (when aikaisin-hoitovuosi-jossa-kirjauksia
                                       (pvm/vuodesta-hoitokausi aikaisin-hoitovuosi-jossa-kirjauksia))
                                   (first mahdolliset-hoitovuodet-lomakkeella))
          johto-ja-hallinto (johto-ja-hallintokorvausmuutoksen-rivit valittu-hoitokausi (:kulut vastaus))
          app (-> app
                ;; Disabloi tallennus, enabloituu itsestään jos lomaketta muutetaan
                ;; Näytä virheet vasta, kun tallenna nappia painetaan (saavutettavuus)
                (assoc
                  :lomake-virheet nil
                  :voi-tallentaa? false
                  :tallenna-painettu? false)
                (dissoc :muutoksen-tiedot-haku-kaynnissa?)
                (assoc-in [:muokattava-muutos :liitteet] uudet-liitteet)
                ;; huom: toimenpiteiden tietoja tarvitaan lisäksi  atomissa joka menee muokkausgridille
                ;; on vielä tutkittava, minne kannattaa säilöä muiden kuin lomakkeella valitun hoitokauden tiedot,
                ;; todennäköisesti app-stateen
                (assoc-in [:muokattava-muutos :toimenpiteiden-tiedot] toimenpiteiden-tiedot)
                (assoc-in [:muokattava-muutos :toimenpiteiden-tehtavat] toimenpiteiden-tehtavat)
                ;; alustetaan lomaketta varten hoitokausi samaksi kuin valittu hoitokausi, mutta ne voivat
                ;; erkaantua myöhemmin jos käyttäjä niin haluaa (esim. kirjata pysyvän muutoksen eri hoitokaudelle kuin valittu)
                (assoc-in [:muokattava-muutos :mahdolliset-hoitovuodet-lomakkeella] mahdolliset-hoitovuodet-lomakkeella)
                (assoc-in [:muokattava-muutos :hoitovuosi] hoitovuosi-lomakkeelle)
                (assoc-in [:muokattava-muutos :johto-ja-hallintokorvaukset] johto-ja-hallinto))]
      app))


  HaeMuutoksenTiedotEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Muutoksen tietojen hakeminen epäonnistui!" :varoitus viesti/viestin-nayttoaika-keskipitka)
    (dissoc app :muutoksen-tiedot-haku-kaynnissa?))


  MuokkaaMuutosta
  (process-event [{:keys [rivi]} app]
    (let [app (if (some? rivi)
                (assoc app :viimeksi-valittu rivi :muokattava-muutos rivi)
                (assoc app :muokattava-muutos rivi))]
      (assoc app
        :lomake-virheet nil
        :tallenna-painettu? false)))


  MuokkaaJohtoJaHallintoMuutosta
  (process-event [{:keys [rivi]} app]
    (-> app
      ;; Tarkoituksella asetetaan trueksi
      ;; tallenna-painettu? sekä lomakevirheet validoivat voidaanko tallentaa (saavutettavuus)
      (assoc :voi-tallentaa? true)
      (assoc-in [:muokattava-muutos :johto-ja-hallintokorvaukset] rivi)))

  PaivitaLomake
  (process-event [{:keys [lomake]} app]
    (let [lomake-virheet (->> lomake ::lomake/virheet vals (map #(str/join " " (map str %))))
          virheita? (empty? (-> lomake ::lomake/virheet vals))]
      (assoc app
        :voi-tallentaa? true
        :lomake-virheet lomake-virheet
        :lomakkeella-virheita? (boolean virheita?)
        :muokattava-muutos (lomake/ilman-lomaketietoja lomake))))

  TallennaMuutos
  (process-event [{:keys [muutos]}
                  {:keys [lomake-virheet laskenta-automatiikka?] :as app}]
    (let [urakka (:urakka @tila/yleiset)
          puuttuvat-pakolliset-kentat (map
                                        #(get pakolliset-kentat-fmt %)
                                        (lomake/puuttuvat-pakolliset-kentat muutos))
          muutos (-> muutos
                   (lomake/ilman-lomaketietoja)
                   (muutos-ilman-ui-tietoja))
          kulut (when (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
                  ;; luodaan vain kuluja, joiden summa on eri suuri kuin 0 (eli niillä on jotain vaikutusta laskentoihin)
                  (filter #(and
                             (some? (:tavoitehinnan-muutos %))
                             (not= 0 (:tavoitehinnan-muutos %)))
                    (vals (:johto-ja-hallintokorvaukset muutos))))

          muutos (assoc muutos :kulut kulut)]

      (if (or
            (some? (vals lomake-virheet))
            (seq puuttuvat-pakolliset-kentat))
        (-> app
          (assoc :voi-tallentaa? false)
          (assoc :tallenna-painettu? true)
          (assoc-in [:muokattava-muutos :puuttuvat-pakolliset-kentat] puuttuvat-pakolliset-kentat))
        (do
          (tuck-apurit/post! :tallenna-muutos
            {:urakka-id (:id urakka)
             :valittu-hoitokausi (:valittu-hoitokausi app)
             :hoitokaudet @u/valitun-urakan-hoitokaudet
             :laskenta-automatiikka? laskenta-automatiikka?
             :muutos muutos}
            {:onnistui ->TallennaMuutosOnnistui
             :epaonnistui ->TallennaMuutosEpaonnistui
             :paasta-virhe-lapi? true})
          (assoc app :tallennus-kesken? true)))))

  TallennaMuutosOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Muutoksen tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)

    (-> app
      ;; Resetoi muutoslomake onnistuneen tallennuksen jälkeen, jotta lomake suljetaan
      (assoc
        :viimeksi-valittu nil
        :muokattava-muutos nil
        :tallennus-kesken? false)
      (vastaus-haku-onnistui vastaus)))

  TallennaMuutosEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Muutoksen tallentaminen epäonnistui! "
                           (get-in vastaus [:response :virhe])) :varoitus viesti/viestin-nayttoaika-pitka)
    (assoc app :tallennus-kesken? false))


  ;; Liitteet
  LisaaLiite
  (process-event
    [{:keys [liite]} app]
    (-> app
      (update-in [:muokattava-muutos :liitteet] conj liite)))

  PoistaLisattyLiite
  (process-event [_ app]
    (assoc app :uusi-liite nil))

  PoistaTallennettuLiite
  (process-event
    [{:keys [liite-id]} app]
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

  PoistaPoistetutLiitteet
  (process-event
    [{:keys [liite-id]} app]
    (let [liitteet (get-in app [:muokattava-muutos :liitteet])]
      (assoc-in app [:muokattava-muutos :liitteet]
        (filter (fn [liite]
                  (not= (:id liite) liite-id))
          liitteet))))


  ;; -- Aika ennen 2025-2026 hoitovuotta -- ALKAA
  LisaaTavoitehintojenMuutos
  (process-event [_ app]
    app)

  LisaaSuunniteltujenMaarienMuutos
  (process-event [_ app]
    app)
  ;; -- Aika ennen 2025-2026 hoitovuotta -- LOPPUU

  ;; -- Siirtymät muutoslomakkeelle muista näkymistä (esim. kustannussuunnitelma) --
  SiirryMuutosNakymaan
  (process-event [_ app]
    (siirtymat/siirry-annettuun-valilehteen @nav/valittu-hallintayksikko-id (-> @tila/yleiset :urakka :id)
      {:taso1 :urakat :taso2 :mhu-muutokset :taso3 nil
       ;; Resetoidaan scroll selaimen yläosaan Muutoksen-näkymään siirtyessä, koska Kustannussuunnitelma-näkymässä
       ;; scrollia ollaan ohjelmallisesti siirretty eri kohtaan
       :resetoi-scroll? true})
    app)

  SiirryPysyvanMuutoksenMuokkauslomakkeelle
  (process-event [{muutos :muutos} app]
    ;; Suoritetaan peräkkäisinä efekteinä viivästettynä
    ;; Ensin siirtymä ja sitten muutoslomakkeen alustus
    (tuck/fx
      app
      {:tuck.effect/type :debounce
       :event ->SiirryMuutosNakymaan
       :timeout 0}
      {:tuck.effect/type :debounce
       :event #(->MuokkaaMuutosta muutos)
       :timeout 100})))
