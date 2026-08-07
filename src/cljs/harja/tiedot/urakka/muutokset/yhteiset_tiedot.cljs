(ns harja.tiedot.urakka.muutokset.yhteiset-tiedot
  "Urakan muutosten tiedot - yhteiset."
  (:require
    [harja.fmt :as fmt]
    [tuck.core :as tuck]
    [clojure.string :as str]
    [reagent.core :refer [atom]]

    [harja.pvm :as pvm]
    [harja.ui.modal :as modal]
    [harja.ui.napit :as napit]
    [harja.tiedot.urakka :as u]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.lomake :as lomake]
    [harja.ui.viesti :as viesti]
    [harja.ui.liitteet :as liitteet]
    [harja.tiedot.navigaatio :as nav]
    [harja.ui.nakymasiirrin :as siirrin]
    [harja.ui.yleiset :as yleiset]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.tiedot.urakka.siirtymat :as siirtymat]
    [harja.domain.kulut.valikatselmus :as valikatselmus]))


(defonce ^{:private true}
  nollatut-valinnat {:haku-kaynnissa? false
                     :muutoksen-tiedot-haku-kaynnissa? false
                     :tallennus-kesken? false
                     :voi-tallentaa? false
                     :testi-nayta-uusi-sivu? false ;; Flagi kehitysympäristöön
                     :lomakkeella-virheita? false
                     :tallenna-painettu? false
                     :muokattava-muutos nil
                     :tavoitehinnan-muutokset nil
                     :suunniteltujen-maarien-muutokset nil
                     :budjettitavoitteet nil
                     :taulukko-nakyvissa? {:kirjatut-muutokset true
                                           :lasketut-muutokset false
                                           :rahavarausten-muutokset false
                                           :tavoitehinnan-muutokset true
                                           :suunniteltujen-maarien-muutokset true}})

(def pakolliset-kentat-fmt {:nimi "Nimi"
                            :tyyppi "Tyyppi"
                            :syy "Muutoksen syy"
                            :voimassa_alkaen "Voimassa alkaen"})

(def +indeksikorjausta-ei-vahvistettu-txt+ "Ei saatavilla")
(def +muutosten-vaikutus-yhteensa-ei-saatavilla+ "Ei saatavilla")
(def uudet-muutokset-kaytossa-alkuvuosi 2025)
(def vanhat-muutokset-kaytossa-alkuvuosi 2021)


(defonce nakymassa? (atom false))


(defn nayta-muutokset-sivu? [alkuvuosi]
  (boolean
    ;; Onko urakan alkuvuosi > x 
    ;; Eli ei valittu aikaväli, vaan urakan alkuvuosi
    (>= (some-> @nav/valittu-urakka :alkupvm pvm/vuosi) alkuvuosi)))

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

;; --- Tuck-eventit ja käsittelijät ---
;; Init
(defrecord AlustaNakyma [])
(defrecord TestiymparistoToggle [])

;; Hae muutostiedot
(defrecord HaeUrakanMuutostiedot [tyyppi])
(defrecord HaeUrakanMuutostiedotOnnistui [vastaus])
(defrecord HaeUrakanMuutostiedotEpaonnistui [vastaus])

;; Vanhojen urakoiden näkymä 
(defrecord HaeVanhanUrakanMuutokset [])
(defrecord HaeValikatselmuksenTiedot [])
(defrecord HaeValikatselmuksenTiedotOnnistui [vastaus])
(defrecord HaeValikatselmuksenTiedotEpaonnistui [vastaus])
(defrecord TallennaOikaisut [oikaisut])
(defrecord TallennaOikaisutOnnistui [vastaus])
(defrecord TallennaOikaisutEpaonnistui [vastaus])

;; Päänäkymä ja listaus
(defrecord ToggleTaulukonNakyvyys [taulukon-avain])
(defrecord ValidoiLomake [lomake])
(defrecord PaivitaLomake [lomake])

(defrecord MuokkaaMuutosta [rivi])
(defrecord MuokkaaJohtoJaHallintoMuutosta [rivi])
(defrecord TallennaMuutos [muutos])
(defrecord TallennaMuutosOnnistui [vastaus])
(defrecord TallennaMuutosEpaonnistui [vastaus])
(defrecord PoistaMuutos [muutos])
(defrecord PoistaMuutosOnnistui [vastaus])
(defrecord PoistaMuutosEpaonnistui [vastaus])


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
(defrecord SiirryPysyvanMuutoksenMuokkauslomakkeelle [muutos valittu-hoitokausi])

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
  (let [hoitokauden-alkuvuosi (some->> @u/valittu-hoitokausi first pvm/vuosi)
        tavoitehinnan-muutokset (vals (get-in (:tavoitehinnan-muutokset vastaus) [hoitokauden-alkuvuosi]))
        tavoitehinnan-muutokset-yhteensa (reduce + 0
                                           (map ::valikatselmus/summa tavoitehinnan-muutokset))]

    (assoc app
      :haku-kaynnissa? false
      :kirjatut-muutokset (:kirjatut-muutokset vastaus)
      :aiempien-hoitovuosien-pysyvat-muutokset (:aiempien-hoitovuosien-pysyvat-muutokset vastaus)
      :tehtava-maaramuutokset (:lasketut-muutokset vastaus)
      :rahavarausten-muutokset (:rahavarausten-muutokset vastaus)
      :tavoitehinnan-muutokset (or tavoitehinnan-muutokset [])
      :tavoitehinnan-muutokset-yhteensa tavoitehinnan-muutokset-yhteensa
      :hoitovuoden-indeksikorjattu-tavoitehinta (-> vastaus :yhteenveto :budjettitavoite :tavoitehinta-indeksikorjattu)
      :suunniteltujen-maarien-muutokset (:suunniteltujen-maarien-muutokset vastaus)
      :laskutusrajan-tarkistukset (:laskutusrajan-tarkistukset vastaus)
      :budjettitavoitteet (:budjettitavoitteet vastaus))))

(defn- laskutusrajan-tarkistus-modaali
  [app vastaus toast-viesti]
  (let [vanha-laskutusraja (get-in app [:budjettitavoitteet :laskutusraja])
        uusi-laskutusraja (get-in vastaus [:budjettitavoitteet :laskutusraja])
        modal-sulje-fn #(do
                          (viesti/nayta-toast! toast-viesti :onnistui viesti/viestin-nayttoaika-lyhyt)
                          (modal/piilota!))]
    (if (and vanha-laskutusraja uusi-laskutusraja
          (not= vanha-laskutusraja uusi-laskutusraja))
      ;; Laskutusraja muuttui: näytä modaali ja toast
      (modal/nayta!
        {:modal-luokka "harja-modal-keskitetty laskutusraja-muutos-modal"
         :luokka "modal-dialog-keskitetty-leveampi"
         :otsikko "Laskutusraja on muuttunut"
         :footer [napit/yleinen-ensisijainen "Sulje" modal-sulje-fn]
         :sulje-fn modal-sulje-fn}
        [:div
         [:p.ylateksti "Laskutusrajaan on tehty automaattinen tarkistus kirjaamasi tavoitehinnan muutoksen perusteella."]
         [yleiset/info-laatikko :vahva-ilmoitus
          (str "Tarkistettu laskutusraja on " (fmt/euro-opt true false uusi-laskutusraja))
          nil
          nil
          {:ikoni-fn #(ikonit/harja-icon-status-alert)}]
         [:p.alateksti "Tarkemmat tiedot laskutusrajan laskennasta ja määräytymisestä näet Muutokset-näkymän \"Laskutusrajan automaattiset tarkistukset\" -osiossa."]])
      ;; Laskutusraja ei muuttunut: näytä vain toast
      (viesti/nayta-toast! toast-viesti :onnistui viesti/viestin-nayttoaika-lyhyt)))

  (-> app
    ;; Resetoi muutoslomake onnistuneen tallennuksen jälkeen, jotta lomake suljetaan
    (assoc
      :viimeksi-valittu nil
      :muokattava-muutos nil
      :tallennus-kesken? false)
    (vastaus-haku-onnistui vastaus)))

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


(defn validoi-pysyvan-muutoksen-vaikutukset
  [muokattava-muutos]
  (let [mahdolliset-hoitovuodet (get-in muokattava-muutos [:mahdolliset-hoitovuodet-lomakkeella])
        alkuvuodet (mapv #(some-> % (first) (pvm/vuosi)) mahdolliset-hoitovuodet)
        rivit (:toimenpiteiden-tiedot muokattava-muutos)]

    (assert (vector? alkuvuodet) "Lomakkeen hoitovuodet puuuttuvat")

    ;; Käydään jokainen toimenpideinstanssi läpi joka hoitovuodelta, ja tarkastetaan onko tavoitehinnan muutos tai
    ;; tehtävän määrämuutos syötetty.
    ;; Jos molemmat syötetty -> OK
    ;; Jos vain toinen syötetty -> Virhetila
    ;; Palautetaan lista virheellisistä toimenpideinstansseista, joista toinen tarpeellinen tieto puuttuu.
    (mapcat
      (fn [rivi]
        (for [alkuvuosi alkuvuodet
              :let [kv (some
                         #(when (= alkuvuosi (:hoitokauden_alkuvuosi %)) %)
                         (:kustannusvaikutukset rivi))

                    tjm (filter
                          #(and (= alkuvuosi (:hoitokauden_alkuvuosi %))
                             (not (:poistettu %)))
                          (:tehtavat_ja_maarat rivi))

                    kv-syotetty? (and kv
                                   (number? (:summa kv))
                                   (not= 0 (:summa kv)))

                    tjm-syotetty? (some
                                    #(and
                                       ;; Validi tehtävä-id (> 0) JA määrämuutos pitää olla syötettynä
                                       (number? (:maaramuutos %))
                                       (number? (:tehtava %))
                                       (pos? (:tehtava %))
                                       (not= 0 (:maaramuutos %)))
                                    tjm)

                    ;; Jos halutaan tallentaa pysyvä muutos ilman tehtävämääriä, vaaditaan syy
                    syy-puuttuu? (and
                                   (false? (:tehtavamaaramuutos-kirjattu? kv))
                                   (str/blank? (:syy kv)))


                    ei-tehtavamuutoksia-ok? (and kv-syotetty?
                                              (false? (:tehtavamaaramuutos-kirjattu? kv))
                                              (not syy-puuttuu?))

                    toinen-syotetty? (or kv-syotetty? tjm-syotetty?)

                    molemmat-ok? (or
                                   (and kv-syotetty? tjm-syotetty?)
                                   ei-tehtavamuutoksia-ok?)]

              ;; Luodaan virhe-map, jos syötetyt tiedot ovat puuttellisia
              :when (or syy-puuttuu?
                      (and toinen-syotetty? (not molemmat-ok?)))]

          {:toimenpideinstanssi (:toimenpideinstanssi rivi)
           :toimenpide (:toimenpide rivi)
           :alkuvuosi alkuvuosi
           :puuttuu (cond
                      syy-puuttuu? :syy
                      kv-syotetty? :maaramuutos
                      :else :tavoitehinnan-muutos)}))
      rivit)))

(defn koosta-pysyvan-muutoksen-lomake-virheet [tpi-vetolaatikoiden-virheet]
  (mapv
    (fn [virhe]
      (str (:alkuvuosi virhe) " / Toimenpide '" (:toimenpide virhe) "': "
        (case (:puuttuu virhe)
          :tavoitehinnan-muutos "Tavoitehinnan muutos"
          :maaramuutos "Vaikutus tehtävämäärään"
          :syy "Tehtävämäärien puutoksen syy")
        " puuttuu."))
    (sort-by :alkuvuosi tpi-vetolaatikoiden-virheet)))

(defn koosta-lomakkeen-validaatio-virheet [lomake]
  (->> lomake ::lomake/virheet vals (map #(str/join " " (map str %)))))

(defn validoi-lomake [lomake]
  (let [pysyvan-muutoksen-virheet (if (= (:tyyppi lomake) "pysyva")
                                    (validoi-pysyvan-muutoksen-vaikutukset lomake)
                                    [])
        lomake-virheet (concat
                         (koosta-lomakkeen-validaatio-virheet lomake)
                         (koosta-pysyvan-muutoksen-lomake-virheet pysyvan-muutoksen-virheet))]
    lomake-virheet))

(extend-protocol tuck/Event

  AlustaNakyma
  (process-event [_ {:keys [testi-nayta-uusi-sivu?] :as app}]
    (let [uusi? (nayta-muutokset-sivu? uudet-muutokset-kaytossa-alkuvuosi)
          ;; testi-nayta-uusi-sivu? 
          ;; on kehitysympäristön testiominaisuus, jolla voi pakottaa uuden näkymän päälle
          uusi? (or testi-nayta-uusi-sivu? uusi?)
          vanha? (and
                   (not uusi?) (not testi-nayta-uusi-sivu?)
                   (nayta-muutokset-sivu? vanhat-muutokset-kaytossa-alkuvuosi))
          ei-kaytossa? (and (not uusi?) (not vanha?))
          app (assoc app :nakyma-uusi? uusi? :nakyma-vanha? vanha?)]

      (if ei-kaytossa?
        app
        (tuck/fx app
          (if uusi?
            {:tuck.effect/type :debounce
             :event #(->HaeUrakanMuutostiedot nil)}

            {:tuck.effect/type :debounce
             :event #(->HaeVanhanUrakanMuutokset)})))))

  TestiymparistoToggle
  (process-event [_ app]
    (let [uusi-nakyma? (not (:testi-nayta-uusi-sivu? app))]
      (assoc app :testi-nayta-uusi-sivu? uusi-nakyma?)))

  HaeVanhanUrakanMuutokset
  (process-event [_ app]
    (tuck/fx
      app
      {:tuck.effect/type :debounce
       :event ->HaeValikatselmuksenTiedot
       :timeout 0}))

  HaeValikatselmuksenTiedot
  (process-event [_ app]
    (tuck-apurit/post! :hae-valikatselmuksen-tiedot-hoitovuodelle
      {:urakkaid (-> @tila/yleiset :urakka :id)
       :hoitovuosi (some->> @u/valittu-hoitokausi first pvm/vuosi)}
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui})
    (assoc app
      :haku-kaynnissa? true
      :valittu-hoitokausi @u/valittu-hoitokausi
      :urakan-hoitokaudet @u/valitun-urakan-hoitokaudet))

  HaeValikatselmuksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (vastaus-haku-onnistui app vastaus))

  HaeValikatselmuksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tavoitehinnan muutosten haku epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  TallennaOikaisut
  (process-event [{:keys [oikaisut]} app]
    (tuck/fx
      (do
        (doseq [oikaisu oikaisut]
          (tuck-apurit/post! :tallenna-tavoitehinnan-oikaisu
            oikaisu
            {:onnistui ->TallennaOikaisutOnnistui
             :epaonnistui ->TallennaOikaisutEpaonnistui
             :paasta-virhe-lapi? true}))
        (assoc app :haku-kaynnissa? true))
      {:tuck.effect/type :debounce
       :event ->HaeValikatselmuksenTiedot
       :timeout 0}))

  TallennaOikaisutOnnistui
  (process-event [{:keys [vastaus]} app]
    app)

  TallennaOikaisutEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Oikaisun tallentaminen epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

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
          hyppaa-hoitovuoteen? (get-in app [:muokattava-muutos :hyppaa-hoitovuoteen?])
          toimenpiteiden-tiedot (:toimenpiteiden-tiedot vastaus)
          toimenpiteiden-tehtavat (:toimenpiteiden-tehtavat vastaus)
          ;; Lomakkeen on kyettävä käsittelemään usealle hoitovuodelle tehtäviä kirjauksia. Kun ländätään lomakkeelle,
          ;; halutaan defaulttina näyttää aikaisin hoitovuosi, jossa on kirjauksia.
          ;; Jos tästä tulee jossain kohti liian hidas, voidaan tarkastelu suorittaa joko backendissä tai tietokannassakin
          aikaisin-hoitovuosi-jossa-kirjauksia (pienin-hoitokauden-alkuvuosi-jossa-kirjauksia toimenpiteiden-tiedot)
          mahdolliset-hoitovuodet-lomakkeella (:urakan-hoitokaudet app)
          hoitovuosi-lomakkeelle (if (and hyppaa-hoitovuoteen? lomakkeen-hoitokausi)
                                   lomakkeen-hoitokausi
                                   (or
                                     (when aikaisin-hoitovuosi-jossa-kirjauksia
                                       (pvm/vuodesta-hoitokausi aikaisin-hoitovuosi-jossa-kirjauksia))
                                     valittu-hoitokausi))

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

  ValidoiLomake
  (process-event [{:keys [lomake]} app]
    ;; Haetaan viimeisin lomakedata suoraan app-tilasta
    (let [lomake-virheet (validoi-lomake lomake)]
      (assoc app
        :voi-tallentaa? true
        :lomake-virheet lomake-virheet
        :lomakkeella-virheita? (boolean (seq lomake-virheet)))))

  ;; PäivitäLomake-eventtiä kutsutaan aina, kun lomakkeen peruskenttiä muutetaan tai hoitovuotta vaihdetaan
  PaivitaLomake
  (process-event [{:keys [lomake]} app]
    (tuck/fx
      (assoc app
        :voi-tallentaa? true
        :muokattava-muutos (lomake/ilman-lomaketietoja lomake))
      ;; Viivästytetään lomakkeen validointia.
      ;; Ei validoida lomaketa joka näppäimenpainalluksella tai muutoksella.
      ;; Debounce-timer resetoituu sisäisesti, mikäli uusi muutos tulee ennen timeouttia (kunhan :id määritetty)
      {:tuck.effect/type :debounce
       :id :paivita-lomake-validaatio
       :event #(->ValidoiLomake lomake)
       :timeout 500}))

  TallennaMuutos
  (process-event [{:keys [muutos]}
                  {:keys [laskenta-automatiikka?] :as app}]
    (let [urakka (:urakka @tila/yleiset)
          kulut (when (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
                  ;; Salli 0 arvo, jos kulu-id on olemassa (se poistetaan)
                  ;; Muuten vaadi, että tavoitehinnan muutos ei ole 0.
                  (filter #(or
                             (:kulu-id %)
                             (not= 0 (:tavoitehinnan-muutos %)))
                    (vals (:johto-ja-hallintokorvaukset muutos))))
          muutos (assoc muutos :kulut kulut)

          puuttuvat-pakolliset-kentat (map
                                        #(get pakolliset-kentat-fmt %)
                                        (lomake/puuttuvat-pakolliset-kentat muutos))
          ;; Pysyvän muutoksen vaikutusten validointi on erikoistapaus, se pitää suorittaa vielä
          ;; tallennuksen yhteydessä. Tämä siksi, että pysyvän muutoksen grid ym. on irtaallaan lomakkeen
          ;; normaalista päivityslogiikasta, ja muutoksia tilaan tulee muistakin eventeistä kuin PaivitaLomake-eventistä.
          ;; Lomakkeen validointia ei kannata viljellä jokaisen pysyvän muutoksen Tuck-eventin yhteyteen, koska
          ;; se lisää turhaa kompleksisuutta ja fragiiliutta.
          lomake-virheet (validoi-lomake muutos)

          ;; Siivotaan lomakeen ylimääräiset tiedot pois ennen lähettämistä backendille
          muutos-payload (-> muutos
                           (lomake/ilman-lomaketietoja)
                           (muutos-ilman-ui-tietoja))]

      (if (or
            (some? (vals lomake-virheet))
            (seq puuttuvat-pakolliset-kentat))
        (-> app
          (assoc :tallenna-painettu? true)
          (assoc :lomake-virheet lomake-virheet)
          (assoc :lomakkeella-virheita? (boolean (seq lomake-virheet)))
          (assoc-in [:muokattava-muutos :puuttuvat-pakolliset-kentat] puuttuvat-pakolliset-kentat))
        (do
          (tuck-apurit/post! :tallenna-muutos
            {:urakka-id (:id urakka)
             :valittu-hoitokausi (:valittu-hoitokausi app)
             :hoitokaudet @u/valitun-urakan-hoitokaudet
             :laskenta-automatiikka? laskenta-automatiikka?
             :muutos muutos-payload}
            {:onnistui ->TallennaMuutosOnnistui
             :epaonnistui ->TallennaMuutosEpaonnistui
             :paasta-virhe-lapi? true})
          (assoc app :tallennus-kesken? true)))))

  TallennaMuutosOnnistui
  (process-event [{:keys [vastaus]} app]
    (laskutusrajan-tarkistus-modaali app vastaus "Muutoksen tallennus onnistui"))

  TallennaMuutosEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Muutoksen tallentaminen epäonnistui! "
                           (get-in vastaus [:response :virhe])) :varoitus viesti/viestin-nayttoaika-pitka)
    (assoc app :tallennus-kesken? false))

  PoistaMuutos
  (process-event [{:keys [muutos]}
                  {:keys [laskenta-automatiikka?] :as app}]
    (let [muutos-id (:id muutos)
          urakka (:urakka @tila/yleiset)]
      (tuck-apurit/post! :poista-muutos
        {:muutos-id muutos-id
         :urakka-id (:id urakka)
         :valittu-hoitokausi (:valittu-hoitokausi app)
         :hoitokaudet @u/valitun-urakan-hoitokaudet
         :laskenta-automatiikka? laskenta-automatiikka?}
        {:onnistui ->PoistaMuutosOnnistui
         :epaonnistui ->PoistaMuutosEpaonnistui
         :paasta-virhe-lapi? true})
      (assoc app :tallennus-kesken? true
        :laskutusraja-ennen-poistoa (get-in app [:budjettitavoitteet :laskutusraja]))))

  PoistaMuutosOnnistui
  (process-event [{:keys [vastaus]} app]
    (laskutusrajan-tarkistus-modaali app vastaus "Muutoksen poistaminen onnistui"))

  PoistaMuutosEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (let [virhe-str (get-in vastaus [:response :virhe] "Tuntematon virhe")]
      (modal/nayta!
        {:otsikko "Muutosta ei voitu poistaa"
         :footer [napit/sulje #(modal/piilota!)]}
        [:div (str virhe-str)])

      ;; Näytä myös toast-viesti, jotta käyttäjä varmasti huomaa virheen tapahtuneen
      ;; Avattu modal antaa tarkempaa lisätietoa.
      (viesti/nayta-toast! (str "Muutoksen poistaminen epäonnistui!") :varoitus viesti/viestin-nayttoaika-keskipitka)

      (assoc app :tallennus-kesken? false)))


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
  (process-event [{:keys [muutos valittu-hoitokausi]} app]
    ;; Suoritetaan peräkkäisinä efekteinä viivästettynä
    ;; Ensin siirtymä ja sitten muutoslomakkeen alustus
    (tuck/fx
      app
      {:tuck.effect/type :debounce
       :event ->SiirryMuutosNakymaan
       :timeout 0}
      {:tuck.effect/type :debounce
       :event #(->MuokkaaMuutosta (assoc muutos :hoitovuosi valittu-hoitokausi :hyppaa-hoitovuoteen? true))
       :timeout 100})))
