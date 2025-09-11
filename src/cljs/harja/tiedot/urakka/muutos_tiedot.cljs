(ns harja.tiedot.urakka.muutos-tiedot
  "Urakan muutosten tiedot."
  (:require [taoensso.timbre :as log]
            [tuck.core :as tuck]
            [reagent.core :refer [atom]]

            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.ui.lomake :as lomake]
            [harja.ui.viesti :as viesti]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]))


(defonce nakymassa? (atom false))
(def +indeksikorjausta-ei-vahvistettu-txt+ "Indeksikorjausta ei saatavilla")
(def johto-ja-hallintokorvausmuutokset-atom (atom nil))
(def muutoksien-kayttoonoton-hoitokauden-alkuvuosi 2025)
(def pakolliset-kentat-fmt {:nimi "Nimi"
                            :tyyppi "Tyyppi"
                            :syy "Muutoksen syy"
                            :voimassa_alkaen "Voimassa alkaen"})
(defonce ^{:private true} nollatut-valinnat {:muokattava-muutos nil
                                             :haku-kaynnissa? false
                                             :tallennus-kesken? false
                                             :kirjatut-muutokset nil
                                             :tehtava-maaramuutokset nil
                                             :rahavarausten-muutokset nil
                                             :tavoitehinnan-muutokset nil
                                             :suunniteltujen-maarien-muutokset nil
                                             :budjettitavoitteet nil
                                             :taulukko-nakyvissa? {:kirjatut-muutokset true
                                                                   :lasketut-muutokset true
                                                                   :rahavarausten-muutokset true
                                                                   :tavoitehinnan-muutokset true
                                                                   :suunniteltujen-maarien-muutokset true}})

;; -- Yleiset ---

;; Hae muutostiedot
(defrecord HaeUrakanMuutostiedot [])
(defrecord HaeUrakanMuutostiedotOnnistui [vastaus])
(defrecord HaeUrakanMuutostiedotEpaonnistui [vastaus])

;; Päänäkymä ja listaus
(defrecord ToggleTaulukonNakyvyys [taulukon-avain])
(defrecord PaivitaLomake [lomake])

(defrecord MuokkaaMuutosta [rivi])
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


;; --- Muutostyyppikohtaiset ---

;; -- Kirjatut muutokset -- ALKAA
;; Pysyvät muutokset
(defrecord KopioiPysyvaMuutosTulevilleHoitovuosille [hoitovuosi rivit])
(defrecord PaivitaToimenpiteenTehtavamaarat [taulukon-rivit])
(defrecord PaivitaToimenpiteenTavoitehinnanMuutos [rivi tpi hk-alkuvuosi])
;; -- Kirjatut muutokset -- LOPPUU

;; -- Lasketut muutokset -- ALKAA
;; Tehtävä- määrämuutokset
(defrecord TallennaTehtavaMaaramuutokset [rivit])
(defrecord TallennaTehtavaMaaramuutoksetOnnistui [vastaus])
(defrecord TallennaTehtavaMaaramuutoksetEpaonnistui [vastaus])
(defrecord AvaaYksikkohintaModal [valittu-modal-tehtava tehtava_id])
(defrecord SuljeYksikkohintaModal [])
(defrecord MuokkaaYksikkohintaa [rivi hoitokausien-yksikkohinnat])
(defrecord TallennaYksikkohinta [rivi])
(defrecord TallennaYksikkohintaOnnistui [vastaus])
(defrecord TallennaYksikkohintaEpaonnistui [vastaus])
;; -- Lasketut muutokset -- LOPPUU

;; -- Rahavarausten muutokset -- ALKAA
(defrecord MuokkaaRahavaraustenMuutoksienSyita [])
(defrecord TallennaRahavarausmuutostenSyyt [rivit])
(defrecord TallennaRahavarausmuutostenSyytEpaonnistui [vastaus])
(defrecord TallennaRahavarausmuutostenSyytOnnistui [vastaus])
;; -- Rahavarausten muutokset -- LOPPUU


;; -- Aika ennen 2025-2026 hoitovuotta
(defrecord LisaaTavoitehintojenMuutos [])
(defrecord LisaaSuunniteltujenMaarienMuutos [])



;; ---------------------------------------------
(defn scrollaa-viimeksi-valitulle-riville []
  (.setTimeout js/window (fn [] (siirrin/kohde-elementti-luokka "viimeksi-valittu-tausta")) 150))


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


(defn hae-urakan-muutostiedot
  "Hakee urakan muutostiedot, eli miten tavoitehinta ja tehtävä- ja määräluettelo ovat muuttuneet alkuperäisiin tietoihin nähden."
  [app]
  (tuck-apurit/post! app :hae-urakan-muutostiedot
    {:urakka-id (-> @tila/yleiset :urakka :id)
     :hoitokaudet @u/valitun-urakan-hoitokaudet
     :valittu-hoitokausi (:valittu-hoitokausi app)}
    {:onnistui ->HaeUrakanMuutostiedotOnnistui
     :epaonnistui ->HaeUrakanMuutostiedotEpaonnistui}))


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
      (filter (fn [liite] (not= (:id liite) liite-id))
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


(defn- vastaus-haku-onnistui [app vastaus]
  (assoc app
    ;; suljetaan aina lomake kun on saatu uudet muutostiedot
    :muokattava-muutos nil
    :haku-kaynnissa? false
    :tallennus-kesken? false
    :kirjatut-muutokset (:kirjatut-muutokset vastaus)
    :tehtava-maaramuutokset (:lasketut-muutokset vastaus)
    :rahavarausten-muutokset (:rahavarausten-muutokset vastaus)
    :tavoitehinnan-muutokset (:tavoitehinnan-muutokset vastaus)
    :suunniteltujen-maarien-muutokset (:suunniteltujen-maarien-muutokset vastaus)
    :budjettitavoitteet (:budjettitavoitteet vastaus)))


;; ------------------------------------
;; Tuck 
(extend-protocol tuck/Event
  HaeUrakanMuutostiedot
  (process-event [_ app]
    (hae-urakan-muutostiedot
      (assoc
        (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
        :haku-kaynnissa? true
        :valittu-hoitokausi @u/valittu-hoitokausi
        :urakan-hoitokaudet @u/valitun-urakan-hoitokaudet)))


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

  PaivitaLomake
  (process-event [{:keys [lomake]} app]
    ;; (prn "PaivitaLomake: " lomake)
    (assoc app :muokattava-muutos lomake))

  ;; Hakee olemassaolevan muutoksen kaikki tiedot muokkausta varten
  HaeMuutoksenTiedot
  (process-event [{:keys [muutos]} app]
    (log/debug "HaeMuutoksenTiedot")
    (when (:id muutos)
      (let [valittu-hoitokausi (:valittu-hoitokausi app)]
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
           :epaonnistui ->HaeMuutoksenTiedotEpaonnistui})))
    app)

  HaeMuutoksenTiedotOnnistui
  (process-event [{vastaus :vastaus
                   muutos :muutos
                   valittu-hoitokausi :valittu-hoitokausi} app]
    (log/debug "HaeMuutoksenTiedotOnnistui")

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
  (process-event [_ app]
    (viesti/nayta-toast! "Muutoksen tietojen hakeminen epäonnistui!" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)


  MuokkaaMuutosta
  (process-event [{:keys [rivi]} app]
    (if (some? rivi)
      (assoc app :viimeksi-valittu rivi :muokattava-muutos rivi)
      (assoc app :muokattava-muutos rivi)))


  TallennaMuutos
  (process-event [{:keys [muutos]} app]
    (let [urakka (:urakka @tila/yleiset)
          puuttuvat-pakolliset-kentat (map
                                        #(get pakolliset-kentat-fmt %)
                                        (lomake/puuttuvat-pakolliset-kentat muutos))
          muutos (lomake/ilman-lomaketietoja muutos)
          kulut (when (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
                  ;; luodaan vain kuluja, joiden summa on eri suuri kuin 0 (eli niillä on jotain vaikutusta laskentoihin)
                  (filter #(and
                             (some? (:tavoitehinnan-muutos %))
                             (not= 0 (:tavoitehinnan-muutos %)))
                    (vals @johto-ja-hallintokorvausmuutokset-atom)))
          muutos (assoc muutos :kulut kulut)]

      (if-not (empty? puuttuvat-pakolliset-kentat)
        (assoc-in app [:muokattava-muutos :puuttuvat-pakolliset-kentat] puuttuvat-pakolliset-kentat)
        (do
          (tuck-apurit/post! :tallenna-muutos
            {:urakka-id (:id urakka)
             :valittu-hoitokausi (:valittu-hoitokausi app)
             :hoitokaudet @u/valitun-urakan-hoitokaudet
             :muutos muutos}
            {:onnistui ->TallennaMuutosOnnistui
             :epaonnistui ->TallennaMuutosEpaonnistui
             :paasta-virhe-lapi? true})
          (assoc app :tallennus-kesken? true)))))

  TallennaMuutosOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Muutoksen tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (vastaus-haku-onnistui app vastaus))

  TallennaMuutosEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Muutoksen tallentaminen epäonnistui! "
                           (get-in vastaus [:response :virhe])) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :tallennus-kesken? false))


  ;; Liitteet
  LisaaLiite
  (process-event
    [{:keys [liite]} app]
    (prn "LisaaLiite")
    (-> app
      (update-in [:muokattava-muutos :liitteet] conj liite)))

  PoistaLisattyLiite
  (process-event [_ app]
    (prn "PoistaLisattyLiite")
    (assoc app :uusi-liite nil))

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

  PoistaPoistetutLiitteet
  (process-event
    [{:keys [liite-id]} app]
    (let [liitteet (get-in app [:muokattava-muutos :liitteet])]
      (assoc-in app [:muokattava-muutos :liitteet]
        (filter (fn [liite]
                  (not= (:id liite) liite-id))
          liitteet))))


  ;; -- Kirjatut muutokset -- ALKAA
  ;; Pysyvät muutokset
  KopioiPysyvaMuutosTulevilleHoitovuosille
  (process-event [{hoitovuosi :hoitovuosi rivit :rivit} app]
    (prn "Tämä on vielä tekemättä")
    ;; TODO: tässä hanskattava muutosten kopiointi tuleville hoitovuosille...
    app)

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

  ;; -- Kirjatut muutokset -- LOPPUU

  ;; ----

  ;; -- Lasketut muutokset --- ALKAA
  ;; Tehtävä- ja määrämuutokset
  TallennaTehtavaMaaramuutokset
  (process-event [{:keys [rivit]}
                  {:keys [_valittu-rivi] :as app}]
    (let [parametrit {:rivit rivit
                      :urakka-id (-> @tila/yleiset :urakka :id)
                      :hoitokaudet @u/valitun-urakan-hoitokaudet
                      :valittu-hoitokausi (:valittu-hoitokausi app)}]
      ;; Kutsutaan gridin tallenna napista, ei modalista 
      (tuck-apurit/post! app :tallenna-tehtava-maaramuutokset
        parametrit
        {:onnistui ->TallennaTehtavaMaaramuutoksetOnnistui
         :epaonnistui ->TallennaTehtavaMaaramuutoksetEpaonnistui})
      (-> app
        (assoc :haku-kaynnissa? true))))

  TallennaTehtavaMaaramuutoksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (assoc app
      :tehtava-maaramuutokset vastaus
      :haku-kaynnissa? false))


  TallennaTehtavaMaaramuutoksetEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Tehtävä- ja määrämuutosten tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  AvaaYksikkohintaModal
  (process-event [{:keys [valittu-modal-tehtava _tehtava_id]}
                  {:keys [_yksikkohinta-modal-auki?] :as app}]
    (assoc app
      :yksikkohinta-modal-auki? true
      :valittu-modal-tehtava valittu-modal-tehtava))


  SuljeYksikkohintaModal
  (process-event [_ {:keys [_yksikkohinta-modal-auki?] :as app}]
    (assoc app
      :valittu-modal-tehtava nil
      :yksikkohinta-modal-auki? false))

  MuokkaaYksikkohintaa
  (process-event [{:keys [rivi hoitokausien-yksikkohinnat]} app]
    (let [valittu-hoitokauden-alkuvuosi (->>
                                          hoitokausien-yksikkohinnat
                                          (filter #(= (:arvo %) (:yksikkohinta rivi)))
                                          first
                                          :hoitokauden-alkuvuosi)]
      ;; Kutsutaan kun modalista valitaan yksikköhinta (ei tallenneta vielä)
      (-> app
        ;; Päivitä yksikköhinta
        (update :valittu-modal-tehtava merge rivi)
        ;; Päivitä valittu yksikköhinnan hk
        (assoc-in [:valittu-modal-tehtava :yksikkohinnan_alkuvuosi] valittu-hoitokauden-alkuvuosi))))

  TallennaYksikkohinta
  (process-event [{:keys [rivi]}
                  {:keys [_valittu-rivi] :as app}]
    ;; Kutsutaan kun modalista tallennetaan valittu yksikköhinta 
    (let [parametrit {:rivi rivi
                      :urakka-id (-> @tila/yleiset :urakka :id)
                      :hoitokaudet @u/valitun-urakan-hoitokaudet
                      :valittu-hoitokausi (:valittu-hoitokausi app)}]

      (tuck-apurit/post! app :tallenna-maaramuutos-yksikkohinta
        parametrit
        {:onnistui ->TallennaYksikkohintaOnnistui
         :epaonnistui ->TallennaYksikkohintaEpaonnistui})
      (-> app
        (assoc
          :haku-kaynnissa? true
          :yksikkohinta-modal-auki? false))))

  TallennaYksikkohintaOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Yksikköhinta tallennettu" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (assoc app
      :haku-kaynnissa? false
      :tehtava-maaramuutokset vastaus))

  TallennaYksikkohintaEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Yksikköhinnan tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  ;; -- Lasketut muutokset --- LOPPUU

  ;; ----

  ;; -- Rahavarausten muutokset -- ALKAA
  MuokkaaRahavaraustenMuutoksienSyita
  (process-event [_ app]
    (assoc app :rahavarausten-syyt-muokattavana? true))

  TallennaRahavarausmuutostenSyyt
  (process-event [{:keys [rivit]} app]
    (let [urakka (:urakka @tila/yleiset)]
      (tuck-apurit/post! :tallenna-rahavarausmuutosten-syyt
        {:urakka-id (:id urakka)
         :hoitokaudet @u/valitun-urakan-hoitokaudet
         :valittu-hoitokausi (:valittu-hoitokausi app)
         :rivit (map #(select-keys % [:id :syy]) rivit)}
        {:onnistui ->TallennaRahavarausmuutostenSyytOnnistui
         :epaonnistui ->TallennaRahavarausmuutostenSyytEpaonnistui})
      app))

  TallennaRahavarausmuutostenSyytEpaonnistui
  (process-event [{:keys [_vastaus]} app]
    (viesti/nayta-toast! "Rahavarauksien muutosten syiden tallentaminen epäonnistui!" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)


  TallennaRahavarausmuutostenSyytOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (vastaus-haku-onnistui app vastaus))


  ;; -- Rahavarausten muutokset -- LOPPUU

  ;; ----

  ;; -- Aika ennen 2025-2026 hoitovuotta -- ALKAA

  LisaaTavoitehintojenMuutos
  (process-event [_ app]
    app)


  LisaaSuunniteltujenMaarienMuutos
  (process-event [_ app]
    app))

;; -- Aika ennen 2025-2026 hoitovuotta -- LOPPUU
