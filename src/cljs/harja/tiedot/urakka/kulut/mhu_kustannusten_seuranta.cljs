(ns harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta
  "UI controlleri kustannusten seurantaan"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.toteuma :as t]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla :as maarien-toteumat-kartalla])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(declare hae-kustannukset)

(defrecord HaeKustannukset [hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm])
(defrecord KustannustenHakuOnnistui [vastaus])
(defrecord KustannustenHakuEpaonnistui [vastaus])
(defrecord HaeBudjettitavoite [])
(defrecord HaeBudjettitavoiteHakuOnnistui [vastaus])
(defrecord HaeBudjettitavoiteHakuEpaonnistui [vastaus])
(defrecord AvaaRivi [tyyppi avain])
(defrecord ValitseHoitokausi [urakka vuosi])

(defn hae-kustannukset [urakka-id hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm]
  (let [alkupvm (when hoitokauden-alkuvuosi
                  (str hoitokauden-alkuvuosi "-10-01"))
        #_alkupvm #_(if aikavali-alkupvm
                      aikavali-alkupvm alkupvm)
        loppupvm (when hoitokauden-alkuvuosi
                   (str (inc hoitokauden-alkuvuosi) "-09-30"))
        #_loppupvm #_(if aikavali-loppupvm
                       aikavali-loppupvm loppupvm)]
    (tuck-apurit/post! :urakan-kustannusten-seuranta-paaryhmittain
                       {:urakka-id urakka-id
                        :alkupvm alkupvm
                        :loppupvm loppupvm}
                       {:onnistui ->KustannustenHakuOnnistui
                        :epaonnistui ->KustannustenHakuEpaonnistui
                        :paasta-virhe-lapi? true})))

;; Raportin pääryhmät jäsennettynä samaan järjestykseen, kuin ui suunnitelmissa on tarkoitettu
(def raportin-paaryhmat
  ["hankintakustannukset", "johto-ja-hallintakorvaus", "hoidonjohdonpalkkio", "erillishankinnat"])

(defn- toimenpide-jarjestys [toimenpide]
  (case (first toimenpide)
    "Talvihoito" 1
    "Liikenneympäristön hoito" 2
    "Sorateiden hoito" 3
    "Päällystepaikkaukset" 4
    "MHU Ylläpito" 5
    "MHU Korvausinvestointi" 6
    "MHU Hoidonjohto" 7))

(defn- summaa-toimenpidetaso [toimenpiteet paaryhmaotsikko]
  (mapv
    (fn [toimenpide]
      (let [toimenpiteen-tehtavat (second toimenpide)
            ;; Toimenpiteet mäpissä on budjetoidut, toteutuneet ja lisätyö toimenpiteet
            ;; UI:lla budjetointi lasketaan yhteen, toteutuneet kustannukset näytetään
            ;; rivikohtaisesti ja lisätyöt erotellaan omaksi rivikseen.
            ;; Poistetaan siis budjetointiin liittyvät tehtävät :toteutunut = budjetoitu tai hth ja lasketaan lisätyöt yhteen.
            toteutuneet-tehtavat (filter
                                   (fn [tehtava]
                                     (when (and
                                             (not= "hjh" (:toteutunut tehtava))
                                             (not= "budjetointi" (:toteutunut tehtava))
                                             (not= "lisatyo" (:maksutyyppi tehtava)))
                                       tehtava))
                                   toimenpiteen-tehtavat)
            jarjestys (some #(:jarjestys %) toimenpiteen-tehtavat)]
        {:paaryhma paaryhmaotsikko
         :toimenpide (first toimenpide)
         :jarjestys jarjestys
         :toimenpide-toteutunut-summa (reduce (fn [summa tehtava]
                                                (+ summa (:toteutunut_summa tehtava)))
                                              0 toteutuneet-tehtavat) ;; vain toteutuneet tehtävät ilman lisätöitä
         :toimenpide-budjetoitu-summa (reduce (fn [summa tehtava]
                                                (+ summa (:budjetoitu_summa tehtava)))
                                              0 toimenpiteen-tehtavat)
         :lisatyot (reduce (fn [summa tehtava]
                             (if (= "lisatyo" (:maksutyyppi tehtava))
                               (+ summa (:toteutunut_summa tehtava))
                               summa))
                           0 toimenpiteen-tehtavat)
         :tehtavat toteutuneet-tehtavat}))
    toimenpiteet))

(defn- summaa-hoito-ja-hallinta-tehtavat [tehtavat paaryhmaotsikko]
  (let [;; Toimenpiteet mäpissä on budjetoidut ja toteutuneet toimenpiteet
        ;; UI:lla budjetointi lasketaan yhteen  ja toteutuneet kustannukset näytetään
        ;; rivikohtaisesti.
        ;; Poistetaan siis budjetointiin liittyvät tehtävät :toteutunut = budjetoitu tai hth
        ;; Jaotellaan tehtävät joko palkkoihin tai toimistokuluihin
        palkkatehtavat (filter (fn [tehtava]
                                 (when (= "palkat" (:toimenpideryhma tehtava))
                                   tehtava))
                               tehtavat)
        toimistotehtavat (filter (fn [tehtava]
                                   (when (= "toimistokulut" (:toimenpideryhma tehtava))
                                     tehtava))
                                 tehtavat)
        toteutuneet-palkat (filter
                             (fn [tehtava]
                               (when (and
                                       (not= "hjh" (:toteutunut tehtava))
                                       (not= "budjetointi" (:toteutunut tehtava)))
                                 tehtava))
                             palkkatehtavat)
        toteutuneet-toimistotehtavat (filter
                                       (fn [tehtava]
                                         (when (and
                                                 (not= "hjh" (:toteutunut tehtava))
                                                 (not= "budjetointi" (:toteutunut tehtava)))
                                           tehtava))
                                       toimistotehtavat)]
    (vec [
          {:paaryhma paaryhmaotsikko
           :toimenpide "Palkat"
           :jarjestys (some #(:jarjestys %) palkkatehtavat)
           :toimenpide-toteutunut-summa (apply + (map (fn [rivi]
                                                        (:toteutunut_summa rivi))
                                                      palkkatehtavat))
           :toimenpide-budjetoitu-summa (apply + (map (fn [rivi]
                                                        (:budjetoitu_summa rivi))
                                                      palkkatehtavat))
           :tehtavat toteutuneet-palkat}
          {:paaryhma paaryhmaotsikko
           :toimenpide "Toimistokulut"
           :jarjestys (some #(:jarjestys %) toimistotehtavat)
           :toimenpide-toteutunut-summa (apply + (map (fn [rivi]
                                                        (:toteutunut_summa rivi))
                                                      toimistotehtavat))
           :toimenpide-budjetoitu-summa (apply + (map (fn [rivi]
                                                        (:budjetoitu_summa rivi))
                                                      toimistotehtavat))
           :tehtavat toteutuneet-toimistotehtavat}])))

(defn- summaa-tehtavat [taulukko-rivit tehtavat indeksi]
  (-> taulukko-rivit
      (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-budjetoitu"))
             (apply + (map (fn [rivi]
                             (:budjetoitu_summa rivi))
                           tehtavat)))
      (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-toteutunut"))
             (apply + (map (fn [rivi]
                             (:toteutunut_summa rivi))
                           tehtavat)))))

(defn- summaa-paaryhman-toimenpiteet [taulukko-rivit indeksi toimenpiteet]
  (-> taulukko-rivit
      (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-budjetoitu"))
             (apply + (map (fn [rivi]
                             (:toimenpide-budjetoitu-summa rivi))
                           toimenpiteet)))
      (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-toteutunut"))
             (apply + (map (fn [rivi]
                             (:toimenpide-toteutunut-summa rivi))
                           toimenpiteet)))
      (assoc :lisatyot (reduce (fn [summa rivi]
                                 (+ summa (:lisatyot rivi)))
                               (:lisatyot taulukko-rivit) toimenpiteet))))

(extend-protocol tuck/Event

  HaeKustannukset
  (process-event [{hoitokauden-alkuvuosi :hoitokauden-alkuvuosi
                   aikavali-alkupvm :aikavali-alkupvm aikavali-loppupvm :aikavali-loppupvm} app]
    (let [alkupvm (when aikavali-alkupvm
                    (pvm/iso8601 aikavali-alkupvm))
          loppupvm (when aikavali-loppupvm
                     (pvm/iso8601 aikavali-loppupvm))
          urakka-id (-> @tila/yleiset :urakka :id)]
      (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm))
    app)

  KustannustenHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [;ryhmitelty-tehtava (group-by :toimenpide vastaus)
          ;vastaus (map #(dissoc % :toimenpidekoodi_nimi :maskutyyppi :luotu :toteutunut :koodi :toimenpideryhma :toimenpideinstanssi_id :toimenpidekoodi_nimi) vastaus)
          paaryhmat (group-by :paaryhma vastaus)
          hankintakustannukset (get paaryhmat (nth raportin-paaryhmat 0)) ;; hankinta
          jjhallinta-kustannukset (get paaryhmat (nth raportin-paaryhmat 1)) ;; johto-ja hallinta..
          hoidonjohdonpalkkiot (get paaryhmat (nth raportin-paaryhmat 2))
          erillishankinnat (get paaryhmat (nth raportin-paaryhmat 3))


          ;; Ryhmittele hankintakustannusten alla olevat tiedot toimenpiteen perusteella
          hankintakustannusten-toimenpiteet (sort-by toimenpide-jarjestys (group-by :toimenpide hankintakustannukset))
          ;_ (js/console.log "hankintakustannusten-toimenpiteet" (pr-str hankintakustannusten-toimenpiteet))
          hankintakustannusten-toimenpiteet (summaa-toimenpidetaso hankintakustannusten-toimenpiteet (nth raportin-paaryhmat 0))
          jjhallinnan-toimenpiteet (summaa-hoito-ja-hallinta-tehtavat jjhallinta-kustannukset (nth raportin-paaryhmat 1))
          hankintakustannusten-toimenpiteet (sort-by :jarjestys hankintakustannusten-toimenpiteet)
          jjhallinnan-toimenpiteet (sort-by :jarjestys jjhallinnan-toimenpiteet)

          taulukon-rivit (-> {}
                             ;; Aseta pääryhmän avaimelle toimenpiteet
                             (assoc (keyword (nth raportin-paaryhmat 0)) hankintakustannusten-toimenpiteet)
                             ;; Aseta pääryhmän avaimaille budjetoitu summa ja toteutunut summa
                             (summaa-paaryhman-toimenpiteet 0 hankintakustannusten-toimenpiteet)
                             (assoc (keyword (nth raportin-paaryhmat 1)) jjhallinnan-toimenpiteet)
                             (summaa-paaryhman-toimenpiteet 1 jjhallinnan-toimenpiteet)
                             (summaa-tehtavat hoidonjohdonpalkkiot 2)
                             (summaa-tehtavat erillishankinnat 3))
          ;; TODO: meander - Ryhmittelyyn sopiva kirjasto, tutustuppa
          yhteensa {:toimenpide "Yhteensä"
                    :yht-toteutunut-summa (apply + (map (fn [pr]
                                                          (get taulukon-rivit (keyword (str pr "-toteutunut"))))
                                                        raportin-paaryhmat))
                    :yht-budjetoitu-summa (apply + (map (fn [pr]
                                                          (get taulukon-rivit (keyword (str pr "-budjetoitu"))))
                                                        raportin-paaryhmat))}]
      (-> app
          (assoc-in [:kustannukset-yhteensa] yhteensa)
          (assoc-in [:kustannukset] vastaus)
          (assoc-in [:kustannukset-grouped1] taulukon-rivit)
          (assoc-in [:kustannukset-grouped2] jjhallinnan-toimenpiteet))))

  KustannustenHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    app)

  HaeBudjettitavoite
  (process-event [_ app]
    (tuck-apurit/post! :budjettitavoite
                       {:urakka-id (-> @tila/yleiset :urakka :id)}
                       {:onnistui ->HaeBudjettitavoiteHakuOnnistui
                        :epaonnistui ->HaeBudjettitavoiteHakuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)

  HaeBudjettitavoiteHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :budjettitavoite vastaus))

  HaeBudjettitavoiteHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Kattohinnan ja tavoitteen haku epäonnistui!" :danger)
    app)

  ;; Vain yksi rivi voi olla avattuna kerralla, joten tallennetaan avain app-stateen tai poistetaan se, jos se oli jo valittuna
  AvaaRivi
  (process-event [{tyyppi :tyyppi avain :avain} app]
    (if (= avain (get-in app [:valittu-rivi tyyppi]))
      (assoc-in app [:valittu-rivi tyyppi] nil)
      (assoc-in app [:valittu-rivi tyyppi] avain)))

  ValitseHoitokausi
  (process-event [{urakka :urakka vuosi :vuosi} app]
    (do
      (hae-kustannukset urakka vuosi nil nil)
      (-> app
          (assoc-in [:hoitokauden-alkuvuosi] vuosi))))

  ;ValitseAikavali
  #_(process-event
      [{:keys [polku arvo]} app]
      (let [arvo (if (nil? arvo)
                   (get-in app [polku])
                   arvo)]
        (-> app
            (assoc-in [:hoitokauden-alkuvuosi] nil)
            (assoc-in [(case polku
                         :alkupvm :aikavali-alkupvm
                         :loppupvm :aikavali-loppupvm)] arvo))))

  )

(defn hoitokauden-jarjestysnumero [valittu-hoitokausivuosi]
  (let [urakka-loppupvm (-> @tila/yleiset :urakka :loppupvm)
        hoitokauden-nro (- 6 (- (pvm/vuosi urakka-loppupvm) valittu-hoitokausivuosi))]
    hoitokauden-nro))

(defn hoitokauden-tavoitehinta [hoitokauden-nro app]
  (let [_ (js/console.log "budjettitavoite" (pr-str (:budjettitavoite app)))
        tavoitehinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                              (:tavoitehinta %))
                           (:budjettitavoite app))]
    tavoitehinta))

(defn hoitokauden-kattohinta [hoitokauden-nro app]
  (let [_ (js/console.log "budjettitavoite" (pr-str (:budjettitavoite app)))
        kattohinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                              (:kattohinta %))
                           (:budjettitavoite app))]
    kattohinta))