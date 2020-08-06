(ns harja.tiedot.urakka.toteumat.maarien-toteumat
  "UI controlleri määrien toteutumille"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.domain.toteuma :as t]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.viesti :as viesti]
            [harja.ui.taulukko.grid :as new-grid]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.tiedot.navigaatio :as nav]
            [harja.loki :as loki])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(declare validoi-lomake)
(declare hae-toteutuneet-maarat)
(declare hae-tehtavat)
(defrecord HaeToimenpiteet [])
(defrecord HaeKaikkiTehtavat [])
(defrecord ToimenpiteetHakuOnnistui [vastaus])
(defrecord ToimenpiteetHakuEpaonnistui [vastaus])
(defrecord ValitseToimenpide [urakka toimenpide])
(defrecord ValitseTehtava [tehtava])
(defrecord ValitseHoitokausi [urakka vuosi])
(defrecord ValitseAikavali [polku arvo])
(defrecord AsetaFiltteri [polku arvo])
(defrecord HaeToteutuneetMaarat [urakka-id toimenpide hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm])
(defrecord ToteutuneetMaaratHakuOnnistui [vastaus])
(defrecord ToteutuneetMaaratHakuEpaonnistui [vastaus])
(defrecord AvaaRivi [avain])
(defrecord MuokkaaToteumaa [toteuma-id])
(defrecord ToteumaHakuOnnistui [vastaus])
(defrecord ToteumaHakuEpaonnistui [vastaus])
(defrecord ToteumanSyotto [auki tehtava toimenpide])
(defrecord TallennaToteuma [])
(defrecord TallennaToteumaOnnistui [vastaus])
(defrecord TallennaToteumaEpaonnistui [vastaus])
(defrecord TehtavatHakuOnnistui [vastaus parametrit])
(defrecord TehtavatHakuEpaonnistui [vastaus])
(defrecord ValidoiKokoLomake [lomake validointi-skeema])

(defrecord HaeTehtavat [parametrit])
(defrecord LahetaLomake [lomake])
(defrecord LisaaToteuma [lomake])
(defrecord PaivitaLomake [lomake])
(defrecord TyhjennaLomake [lomake])

(def tyyppi->tyyppi
  {"kokonaishintainen"     "maaramitattava"
   "muut-rahavaraukset"    "tilaajan-varaukset"
   "lisatyo"               "lisatyo"
   "vahinkojen-korjaukset" "vahinkojen-korjaukset"
   "akillinen-hoitotyo"    "akillinen-hoitotyo"})

(def oletuslomake {})

(def uusi-toteuma {})

(defn validoinnit
  ([avain lomake indeksi]
   (let []
     (avain {::t/maara      [tila/ei-nil tila/ei-tyhja tila/numero]
             ::t/lisatieto  [(tila/silloin-kun #(= :lisatyo (::t/tyyppi lomake))
                                               tila/ei-nil)
                             (tila/silloin-kun #(= :lisatyo (::t/tyyppi lomake))
                                               tila/ei-tyhja)]
             ::t/toimenpide [tila/ei-nil tila/ei-tyhja]
             ::t/tehtava    [tila/ei-nil tila/ei-tyhja]
             ::t/sijainti   [(tila/silloin-kun #(nil? (get-in lomake [::t/toteumat indeksi ::t/ei-sijaintia]))
                                               tila/ei-nil)]
             ::t/tyyppi     [tila/ei-nil]
             ::t/pvm        [tila/ei-nil tila/ei-tyhja tila/paivamaara]})))
  ([avain lomake]
   (validoinnit avain lomake 0))
  ([avain]
   (validoinnit avain {} 0)))

(def toteuma-lomakkeen-oletus-validoinnit
  [[::t/toimenpide] (validoinnit ::t/toimenpide)
   [::t/pvm] (validoinnit ::t/pvm)
   [::t/tyyppi] (validoinnit ::t/tyyppi)])

(defn toteuma-lomakkeen-validoinnit [{toteumat ::t/toteumat :as lomake}]
  (apply tila/luo-validius-tarkistukset
         (concat toteuma-lomakkeen-oletus-validoinnit
                 (mapcat (fn [i]
                           [[::t/toteumat i ::t/maara] (validoinnit ::t/maara)
                            [::t/toteumat i ::t/tehtava] (validoinnit ::t/tehtava)
                            [::t/toteumat i ::t/sijainti] (validoinnit ::t/sijainti lomake i)
                            [::t/toteumat i ::t/lisatieto] (validoinnit ::t/lisatieto lomake)])
                         (range (count toteumat))))))

(defn- hae-tehtavat-tyypille
  ([toimenpide]
   (hae-tehtavat-tyypille toimenpide :maaramitattava))
  ([toimenpide tyyppi]
   (loki/log "haen tehtavat tyypille" toimenpide tyyppi)
   (let [tehtavaryhma (when toimenpide
                        (:otsikko toimenpide))
         rajapinta (case tyyppi
                     :akillinen-hoitotyo :akillisten-hoitotoiden-toimenpiteiden-tehtavat
                     :lisatyo :lisatoiden-toimenpiteiden-tehtavat
                     :maarien-toteutumien-toimenpiteiden-tehtavat)
         toimenpide-re-string (when toimenpide
                                (cond
                                  (re-find #"TALVIHOITO" tehtavaryhma) "alvihoito"
                                  (re-find #"LIIKENNEYMPÄRISTÖN HOITO" tehtavaryhma) "Liikenneympäristön hoito|l.ymp.hoito"
                                  (re-find #"SORATEIDEN HOITO" tehtavaryhma) "Soratiet|sorateiden"
                                  :else ""))
         parametrit {:polku    :tehtavat
                     :filtteri (case tyyppi
                                 :akillinen-hoitotyo
                                 #(re-find (re-pattern (str "(" toimenpide-re-string "|rahavaraus)")) (:tehtava %))

                                 :lisatyo
                                 #(re-find (re-pattern (str "(" toimenpide-re-string ")")) (:tehtava %))

                                 (constantly true))}]
     (tuck-apurit/post! rajapinta
                        {:tehtavaryhma tehtavaryhma
                         :urakka-id    (-> @tila/yleiset :urakka :id)}
                        {:onnistui            ->TehtavatHakuOnnistui
                         :onnistui-parametrit [parametrit]
                         :epaonnistui         ->TehtavatHakuEpaonnistui
                         :paasta-virhe-lapi?  true}))))

(def filtteri->tyyppi {:maaramitattavat #{"kokonaishintainen"}
                       :lisatyot        #{"lisatyo"}
                       :rahavaraukset   #{"akillinen-hoitotyo" "muut-rahavaraukset" "vahinkojen-korjaukset"}})
(defn- tehtavien-filtteri-fn
  [filtterit]
  (fn [tehtava]
    (cond
      (nil? filtterit)
      true
      
      (contains?
        (into #{}
              (mapcat filtteri->tyyppi
                      (keys
                        (into {}
                              (filter (fn [[_ arvo]]
                                        (true? arvo))
                                      filtterit)))))
        (:tyyppi tehtava))
      true

      :else
      false)))

(defn ryhmittele-tehtavat
  [ryhmiteltavat filtterit]
  (let [ryhmiteltavat-filtteroitu (filter (tehtavien-filtteri-fn filtterit)
                                          ryhmiteltavat)
        ryhmitelty-tr (group-by :tehtavaryhma
                                ryhmiteltavat-filtteroitu)]
    (sort-by first
             (into {}
                   (map
                     (fn [[tehtavaryhma tehtavat]]
                       [tehtavaryhma (sort-by first
                                              (group-by :tehtava
                                                        tehtavat))])
                     ryhmitelty-tr)))))

(defn- aseta-akillisen-tyyppi
  [toteumat t]
  (if (= t :akillinen-hoitotyo)
    (let [{{:keys [tehtava]} ::t/tehtava} (first toteumat)]
      (cond
        (re-find #"ahavarau" tehtava) :tilaajan-varaukset
        (re-find #"korjaukset" tehtava) :vahinkojen-korjaukset
        :else t))
    t))

(extend-protocol tuck/Event
  AsetaFiltteri
  (process-event [{polku :polku arvo :arvo} app]
    (as-> app app
          (assoc-in app [:hakufiltteri polku] arvo)
          (assoc-in app [:toteutuneet-maarat-grouped] (ryhmittele-tehtavat (:toteutuneet-maarat app)
                                                                           (:hakufiltteri app)))))
  HaeTehtavat
  (process-event [{{:keys [toimenpide]} :parametrit} app]
    (hae-tehtavat-tyypille toimenpide)
    app)
  LahetaLomake
  (process-event [{lomake :lomake} app]
    (let [{loppupvm   ::t/pvm
           tyyppi     ::t/tyyppi
           toimenpide ::t/toimenpide
           toteumat   ::t/toteumat} lomake
          urakka-id (-> @tila/yleiset :urakka :id)
          aseta-akillisen-tyyppi (r/partial aseta-akillisen-tyyppi
                                            toteumat)
          {:keys [validoi] :as validoinnit} (toteuma-lomakkeen-validoinnit lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)]
      (if (true? validi?)
        (tuck-apurit/post! :tallenna-toteuma
                           {:urakka-id  urakka-id
                            :toimenpide toimenpide
                            :tyyppi     (aseta-akillisen-tyyppi tyyppi)
                            :loppupvm   loppupvm
                            :toteumat   (mapv #(into {}     ; siivotaan namespacet lähetettävästä
                                                     (map
                                                       (fn [[k v]]
                                                         [(-> k name keyword) v])
                                                       %))
                                              toteumat)}
                           {:onnistui    ->TallennaToteumaOnnistui
                            :epaonnistui ->TallennaToteumaEpaonnistui})
        (viesti/nayta! "Puuttuvia tai virheellisiä kenttiä, tarkista kentät!" :danger))
      (-> app
          (assoc-in [:lomake ::tila/validius] validius)
          (assoc-in [:lomake ::tila/validi?] validi?))))
  LisaaToteuma
  (process-event [{lomake :lomake} app]
    (let [lomake (update lomake ::t/toteumat conj uusi-toteuma)]
      (assoc app :lomake lomake)))
  ValidoiKokoLomake
  (process-event [{lomake            :lomake
                   validointi-skeema :validointi-skeema} app]
    (loki/log (toteuma-lomakkeen-validoinnit lomake))
    app)
  PaivitaLomake
  (process-event [{{useampi?          ::t/useampi-toteuma
                    tyyppi            ::t/tyyppi
                    toimenpide        ::t/toimenpide
                    viimeksi-muokattu ::ui-lomake/viimeksi-muokattu-kentta
                    :as               lomake} :lomake} app]
    ; sivuvaikutusten triggeröintiin
    (case viimeksi-muokattu
      ::t/toimenpide (hae-tehtavat-tyypille toimenpide tyyppi)
      ::t/tyyppi (hae-tehtavat-tyypille toimenpide tyyppi)
      :default)
    ; :TODO: tässä tai jossain pitää validoida lomake erikseen, koska se harja.ui.lomake-lomakkeen oma vaikutti liian tunkkaiselta tähän, parempi tehä ite
    (let [useampi-aiempi? (get-in app [:lomake ::t/useampi-toteuma])
          tyyppi-aiempi (get-in app [:lomake ::t/tyyppi])
          app (assoc app :lomake lomake)
          vain-eka (fn [ts]
                     [(first ts)])
          maara-pois (fn [ts]
                       (mapv #(dissoc % ::t/maara) ts))
          uusi-app (update app :lomake (fn [l]
                                         (let [l (cond-> l
                                                         ; lisätään uusi toteumamappi, jos useampi toteuma- checkboxia klikattu
                                                         (and (true? useampi?)
                                                              (= tyyppi :maaramitattava)
                                                              (not= useampi? useampi-aiempi?))
                                                         (update ::t/toteumat conj uusi-toteuma)

                                                         ; tai resetoidaan
                                                         (and (not (true? useampi?))
                                                              (= tyyppi :maaramitattava)
                                                              (not= useampi? useampi-aiempi?))
                                                         (update ::t/toteumat #(conj [] (first %)))

                                                         ; onko toteumia poistettu, jos niin asetetaan useampi-toteuma oikein
                                                         (and (true? useampi?)
                                                              (true? useampi-aiempi?)
                                                              (= (count (::t/toteumat l)) 1))
                                                         (assoc ::t/useampi-toteuma false))]
                                           ; siivotaan tyyppiä vaihdettaessa turhat kentät
                                           (if (not= tyyppi tyyppi-aiempi)
                                             (case tyyppi
                                               :akillinen-hoitotyo (update l ::t/toteumat (comp vain-eka maara-pois))
                                               :lisatyo (update l ::t/toteumat (comp vain-eka maara-pois))
                                               l)
                                             l))))]
      #_(-> app
            (update :lomake (if (and
                                  (= tyyppi :maaramitattava)
                                  (not= useampi? useampi-aiempi?))
                              (fn [lomake]
                                (if (true? useampi?)
                                  (update lomake ::t/toteumat conj uusi-toteuma)
                                  (update lomake ::t/toteumat #(conj [] (first %)))))
                              identity)))
      uusi-app))
  TyhjennaLomake
  (process-event [_ app]
    (assoc app :syottomoodi false
               :lomake (-> tila/toteumat-default-arvot
                           :maarien-toteumat
                           :lomake)))





  ValitseToimenpide
  (process-event [{urakka :urakka toimenpide :toimenpide} app]
    (do
      (js/console.log "ValitseToimenpide" (pr-str toimenpide))
      (hae-toteutuneet-maarat urakka toimenpide
                              (:hoitokauden-alkuvuosi app)
                              (:aikavali-alkupvm app)
                              (:aikavali-loppupvm app))
      (hae-tehtavat toimenpide)
      (-> app
          (assoc :valittu-toimenpide toimenpide)
          (assoc-in [:lomake ::t/toimenpide] toimenpide)
          (assoc-in [:lomake ::t/toteumat 0 ::t/tehtava] nil)
          (validoi-lomake))))

  ValitseTehtava
  (process-event [{tehtava :tehtava} app]
    (do
      (js/console.log "ValitseTehtava" (pr-str tehtava))
      (-> app
          (assoc-in [:toteuma :tehtava] tehtava)
          (validoi-lomake))))

  ;AsetaMaara
  #_(process-event [{maara :maara} app]
                   (do
                     (js/console.log "AsetaMaara" (pr-str maara))
                     (-> app
                         (assoc-in [:toteuma :maara] maara)
                         (validoi-lomake))))

  ;AsetaLisatieto
  #_(process-event [{arvo :arvo} app]
                   (do
                     (js/console.log "AsetaLisatieto" (pr-str arvo))
                     (-> app
                         (assoc-in [:toteuma :lisatieto] arvo)
                         (validoi-lomake))))

  ;AsetaLoppuPvm
  #_(process-event [{arvo :arvo} app]
                   (do
                     (js/console.log "AsetaLoppuPvm" (pr-str arvo) (pr-str (pvm/pvm arvo)))
                     (-> app
                         (assoc-in [:toteuma :loppupvm] arvo)
                         (validoi-lomake))))

  ;; Vain yksi rivi voi olla avattuna kerralla, joten tallennetaan avain app-stateen tai poistetaan se, jos se oli jo valittuna
  AvaaRivi
  (process-event [{avain :avain} app]
    (do
      (js/console.log "AvaaRivi" (pr-str avain))
      (if (= avain (get-in app [:valittu-rivi]))
        (assoc-in app [:valittu-rivi] nil)
        (assoc-in app [:valittu-rivi] avain))))

  MuokkaaToteumaa
  (process-event [{toteuma-id :toteuma-id} app]
    (js/console.log "MuokkaaToteumaa" (pr-str toteuma-id))
    (tuck-apurit/post! :hae-maarien-toteuma {:id toteuma-id :urakka-id (:id @nav/valittu-urakka)}
                       {:onnistui           ->ToteumaHakuOnnistui
                        :epaonnistui        ->ToteumaHakuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)                                                    ;(assoc app [:syottomoodi] true)

  ToteumaHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [_ (js/console.log "ToteumaHakuOnnistui :: vastaus " (pr-str vastaus))
          valittu-tehtava {:id (:tehtava_id vastaus) :tehtava (:tehtava vastaus) :yksikko (:yksikko vastaus)}
          valittu-toimenpide {:id (:toimenpide_id vastaus) :otsikko (:toimenpide_otsikko vastaus)}
          sijainti {:numero        (:sijainti_numero vastaus)
                    :alkuosa       (:sijainti_alku vastaus)
                    :alkuetaisyys  (:sijainti_alkuetaisyys vastaus)
                    :loppuosa      (:sijainti_loppu vastaus)
                    :loppuetaisyys (:sijainti_loppuetaisyys vastaus)}]
      (hae-tehtavat valittu-toimenpide)
      (-> app
          (assoc-in [:syottomoodi] true)
          (assoc-in [:lomake ::t/toteumat 0 ::t/toteuma-id] (:toteuma_id vastaus))
          (assoc-in [:lomake ::t/toteumat 0 ::t/toteuma-tehtava-id] (:toteuma_tehtava_id vastaus))
          (assoc-in [:lomake ::t/pvm] (:toteuma_aika vastaus))
          (assoc-in [:lomake ::t/toteumat 0 ::t/maara] (:toteutunut vastaus))
          (assoc-in [:lomake ::t/toteumat 0 ::t/tehtava] valittu-tehtava)
          (assoc-in [:lomake ::t/toteumat 0 ::t/sijainti] sijainti)
          (assoc-in [:lomake ::t/toteumat 0 ::t/ei-sijaintia] (some #(nil? (second %)) sijainti))
          (assoc-in [:lomake ::t/tyyppi] (-> vastaus :tyyppi tyyppi->tyyppi keyword))
          (assoc-in [:lomake ::t/toimenpide] valittu-toimenpide)
          (assoc-in [:lomake :vuosi] (:hoitokauden-alkuvuosi vastaus))
          (validoi-lomake))))

  ToteumaHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toteuman haku epäonnistui!" :danger)
    (js/console.log "ToteumaHakuEpaonnistui :: vastaus" (pr-str vastaus))
    app)

  ToimenpiteetHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc-in app [:toimenpiteet] vastaus))

  ToimenpiteetHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (js/console.log "ToimenpiteetHakuEpaonnistui :: vastaus" (pr-str vastaus))
    app)

  ValitseHoitokausi
  (process-event [{urakka :urakka vuosi :vuosi} app]
    (do
      (js/console.log "ValitseHoitokausi" (pr-str vuosi))
      (hae-toteutuneet-maarat urakka (:valittu-toimenpide app) vuosi nil nil)
      (-> app
          (assoc-in [:hoitokauden-alkuvuosi] vuosi)
          (assoc-in [:toteuma :aikavali-alkupvm] nil)
          (assoc-in [:toteuma :aikavali-loppupvm] nil))))

  ValitseAikavali
  (process-event
    [{:keys [polku arvo]} app]
    (let [_ (js/console.log "ValitseAikavali :: polku arvo" (pr-str polku) (pr-str arvo))
          arvo (if (nil? arvo)
                 (get-in app [polku])
                 arvo)]
      (-> app
          (assoc-in [:hoitokauden-alkuvuosi] nil)
          (assoc-in [(case polku
                       :alkupvm :aikavali-alkupvm
                       :loppupvm :aikavali-loppupvm)] arvo))))

  HaeToteutuneetMaarat
  (process-event [{urakka-id        :urakka-id toimenpide :toimenpide hoitokauden-alkuvuosi :hoitokauden-alkuvuosi
                   aikavali-alkupvm :aikavali-alkupvm aikavali-loppupvm :aikavali-loppupvm} app]
    (let [alkupvm (when aikavali-alkupvm
                    (pvm/iso8601 aikavali-alkupvm))
          loppupvm (when aikavali-loppupvm
                     (pvm/iso8601 aikavali-loppupvm))
          _ (js/console.log "HaeToteutuneetMaarat :: aikavalit" (pr-str alkupvm) "-" (pr-str alkupvm) (pr-str loppupvm))]
      (hae-toteutuneet-maarat urakka-id toimenpide hoitokauden-alkuvuosi alkupvm loppupvm))
    app)

  HaeToimenpiteet
  (process-event [_ app]
    (tuck-apurit/post! :urakan-toteumien-toimenpiteet {}
                       {:onnistui           ->ToimenpiteetHakuOnnistui
                        :epaonnistui        ->ToimenpiteetHakuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)

  HaeKaikkiTehtavat
  (process-event [_ app]
    (hae-tehtavat nil)
    app)

  ToteutuneetMaaratHakuOnnistui
  (process-event [{vastaus :vastaus hakufiltterit :hakufiltteri} app]
    (let [ryhmitelty-tehtava (ryhmittele-tehtavat vastaus hakufiltterit)]
      (-> app
          (assoc-in [:toteutuneet-maarat] vastaus)
          (assoc-in [:toteutuneet-maarat-grouped] ryhmitelty-tehtava))))

  ToteutuneetMaaratHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    (js/console.log "ToteutuneetMaaratHakuEpaonnistui :: vastaus" (pr-str vastaus))
    app)

  TehtavatHakuOnnistui
  (process-event [{vastaus :vastaus {:keys [filtteri]} :parametrit} app]
    (-> app
        (assoc-in [:tehtavat] (if filtteri
                                (filter filtteri vastaus)
                                vastaus))
        (validoi-lomake)))

  TehtavatHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    (js/console.log "TehtavatHakuEpaonnistui :: vastaus" (pr-str vastaus))
    app)

  ToteumanSyotto
  (process-event [{auki :auki tehtava :tehtava toimenpide :toimenpide} app]
    (js/console.log "ToteumanSyotto "
                    (pr-str auki)
                    (pr-str tehtava) (pr-str toimenpide) "kuukausi " (pr-str (pvm/kuukausi (pvm/nyt))) (:hoitokauden-alkuvuosi app))

    (cond-> app
            (not (nil? tehtava)) (assoc-in [:lomake ::t/toteumat 0 ::t/tehtava] tehtava)
            (and
              (not (nil? toimenpide))
              (not= {:otsikko "Kaikki" :id 0} toimenpide)) (assoc-in [:lomake ::t/toimenpide] toimenpide)
            (= {:otsikko "Kaikki" :id 0} toimenpide) (assoc-in [:lomake ::t/toimenpide] nil)
            true (assoc-in [:syottomoodi] auki)
            true (assoc-in [:lomake ::t/toteumat 0 ::t/lisatieto] nil)
            true (assoc-in [:lomake ::t/toteumat 0 ::t/maara] nil)
            true (assoc-in [:lomake ::t/pvm] (pvm/luo-pvm (if (> (pvm/kuukausi (pvm/nyt)) 10)
                                                            (:hoitokauden-alkuvuosi app)
                                                            (+ 1 (:hoitokauden-alkuvuosi app)))
                                                          (- (pvm/kuukausi (pvm/nyt)) 1)
                                                          1))))

  ;PoistaToteuma
  #_(process-event [{id :id} app]
                   (js/console.log "Poista Toteuma - tää ei tekis vielä mittään!!" (pr-str id))
                   app)

  TallennaToteuma
  (process-event [_ app]
    (let [urakka-id (:id @nav/valittu-urakka)
          toimenpide (get-in app [:toteuma :toimenpide])
          tehtava (get-in app [:toteuma :tehtava])
          maara (get-in app [:toteuma :maara])
          loppupvm (get-in app [:toteuma :loppupvm])
          lisatieto (get-in app [:toteuma :lisatieto])]
      (tuck-apurit/post! :tallenna-toteuma
                         {:toteuma-id         (get-in app [:toteuma :toteuma-id])
                          :toteuma-tehtava-id (get-in app [:toteuma :toteuma-id])
                          :urakka-id          urakka-id
                          :tehtavaryhma       (:otsikko toimenpide)
                          :maara              maara
                          :tehtava            tehtava
                          :loppupvm           (pvm/pvm loppupvm)
                          :lisatieto          lisatieto}
                         {:onnistui           ->TallennaToteumaOnnistui
                          :epaonnistui        ->TallennaToteumaEpaonnistui
                          :paasta-virhe-lapi? true}))
    app)

  TallennaToteumaOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toteuma tallennettu!")
    ;; Päivitä määrät välittömästi lisäyksen jälkeen
    (hae-toteutuneet-maarat (:id @nav/valittu-urakka)
                            (:valittu-toimenpide app)
                            (get-in app [:hoitokauden-alkuvuosi])
                            (get-in app [:aikavali-alkupvm])
                            (get-in app [:aikavali-loppupvm]))
    (assoc app :syottomoodi false))

  TallennaToteumaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toteuman tallennus epäonnistui!" :danger)
    app)
  )

(defn hae-toteutuneet-maarat [urakka-id toimenpide hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm]
  (let [alkupvm (when hoitokauden-alkuvuosi
                  (str hoitokauden-alkuvuosi "-10-01"))
        alkupvm (if aikavali-alkupvm
                  aikavali-alkupvm alkupvm)
        loppupvm (when hoitokauden-alkuvuosi
                   (str (inc hoitokauden-alkuvuosi) "-09-30"))
        loppupvm (if aikavali-loppupvm
                   aikavali-loppupvm loppupvm)]
    (tuck-apurit/post! :urakan-maarien-toteumat
                       {:urakka-id    urakka-id
                        :tehtavaryhma (:otsikko toimenpide)
                        :alkupvm      alkupvm
                        :loppupvm     loppupvm}
                       {:onnistui           ->ToteutuneetMaaratHakuOnnistui
                        :epaonnistui        ->ToteutuneetMaaratHakuEpaonnistui
                        :paasta-virhe-lapi? true})))

(defn- hae-tehtavat [toimenpide]
  (let [tehtavaryhma (when toimenpide
                       (:otsikko toimenpide))]
    (tuck-apurit/post! :maarien-toteutumien-toimenpiteiden-tehtavat
                       {:tehtavaryhma tehtavaryhma}
                       {:onnistui           ->TehtavatHakuOnnistui
                        :epaonnistui        ->TehtavatHakuEpaonnistui
                        :paasta-virhe-lapi? true})))

(defn- validoi-lomake [app]
  (let [toimenpiteella-tehtavia? (> (count (get-in app [:tehtavat])) 0)
        tehtava-valittu? (if toimenpiteella-tehtavia?
                           (not (nil? (get-in app [:toteuma :tehtava])))
                           true)
        valid? (if (and
                     (not (nil? (:valittu-toimenpide app)))
                     tehtava-valittu?
                     (not (nil? (get-in app [:toteuma :maara])))
                     (not (nil? (get-in app [:toteuma :loppupvm]))))
                 true
                 false)]
    (assoc-in app [:lomake-validoitu?] valid?)))

(defn paivita-raidat! [g]
  (let [paivita-luokat (fn [luokat odd?]
                         (if odd?
                           (-> luokat
                               (conj "table-default-odd")
                               (disj "table-default-even"))
                           (-> luokat
                               (conj "table-default-even")
                               (disj "table-default-odd"))))]
    (loop [[rivi & loput-rivit] (new-grid/nakyvat-rivit g)
           index 0]
      (if rivi
        (let [rivin-nimi (new-grid/hae-osa rivi :nimi)]
          (new-grid/paivita-grid! rivi
                                  :parametrit
                                  (fn [parametrit]
                                    (update parametrit :class (fn [luokat]
                                                                (if (= ::valinta rivin-nimi)
                                                                  (paivita-luokat luokat (not (odd? index)))
                                                                  (paivita-luokat luokat (odd? index)))))))
          (recur loput-rivit
                 (if (= ::valinta rivin-nimi)
                   index
                   (inc index))))))))

(defn uusi-gridi [dom-id]
  (new-grid/grid {:nimi          ::root
                  :dom-id        dom-id
                  :root-fn       (fn [] (get-in @tila/toteumat-maarien-toteumat [:gridit :maarien-toteumat :grid]))
                  :paivita-root! (fn [f]
                                   (swap! tila/toteumat-maarien-toteumat
                                          (fn [tila]
                                            (update-in tila [:gridit :maarien-toteumat :grid] f))))
                  :alueet        [{:sarakkeet [0 1] :rivit [0 2]}]
                  :koko          (-> konf/auto
                                     (assoc-in [:rivi :nimet]
                                               {::otsikko 0
                                                ::data    1})
                                     (assoc-in [:rivi :korkeudet] {0 "40px"}))
                  :osat          [(new-grid/rivi {:nimi ::otsikko
                                                  :koko (-> konf/livi-oletuskoko
                                                            (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                           3 "1fr"})
                                                            (assoc-in [:sarake :oletus-leveys] "2fr"))
                                                  :osat (vec (repeatedly 4 #(solu/teksti {:parametrit {:class #{"table-default" "table-default-header"}}})))}
                                                 [{:sarakkeet [0 4] :rivit [0 1]}])
                                  (new-grid/dynamic-grid {:nimi                   ::data
                                                          :alueet                 [{:sarakkeet [0 1] :rivit [0 1]}]
                                                          :koko                   konf/auto
                                                          :osien-maara-muuttui!   (fn [g _] (paivita-raidat! (new-grid/osa-polusta (new-grid/root g) [::data])))
                                                          :toistettavan-osan-data (constantly [{:hoitokauden-alkuvuosi 2019
                                                                                                :id                    6
                                                                                                :suunniteltu_maara     40
                                                                                                :tehtava               "Pysäkkikatoksen uusiminen"
                                                                                                :toteuma_aika          nil
                                                                                                :toteutunut            nil
                                                                                                :urakka                35
                                                                                                :yksikko               "kpl"}])
                                                          #_(fn [{:keys [arvot valittu-toimenpide hoitokauden-numero]}]
                                                              {:valittu-toimenpide valittu-toimenpide
                                                               :hoitokauden-numero hoitokauden-numero
                                                               :tyypit             (mapv key arvot)})
                                                          :toistettava-osa        (fn [vectori]
                                                                                    (mapv (fn [rivi]
                                                                                            (with-meta
                                                                                              (new-grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                                                                                                              :nimi   ::datarivi
                                                                                                              :koko   (-> konf/auto
                                                                                                                          (assoc-in [:rivi :nimet]
                                                                                                                                    {::data-yhteenveto 0
                                                                                                                                     ::data-sisalto    1}))
                                                                                                              :osat   [(with-meta
                                                                                                                         (new-grid/rivi {:nimi ::data-yhteenveto
                                                                                                                                         :koko {:seuraa {:seurattava ::otsikko
                                                                                                                                                         :sarakkeet  :sama
                                                                                                                                                         :rivit      :sama}}
                                                                                                                                         :osat [(solu/laajenna {:auki-alussa? false
                                                                                                                                                                :parametrit   {:class #{"table-default" "lihavoitu"}}})
                                                                                                                                                (solu/teksti {:parametrit {:class #{"table-default" "table-default-header"}}})
                                                                                                                                                (solu/teksti {:parametrit {:class #{"table-default"}}})
                                                                                                                                                (solu/teksti {:parametrit {:class #{"table-default"}}})]}
                                                                                                                                        [{:sarakkeet [0 4] :rivit [0 1]}])
                                                                                                                         {:key (str rivi "-yhteenveto")})
                                                                                                                       (with-meta
                                                                                                                         (new-grid/dynamic-grid {:nimi                   ::data-sisalto
                                                                                                                                                 :alueet                 [{:sarakkeet [0 1] :rivit [0 12]}]
                                                                                                                                                 :koko                   konf/auto
                                                                                                                                                 :luokat                 #{"piillotettu" "salli-ylipiirtaminen"}
                                                                                                                                                 :toistettavan-osan-data (constantly [{:hoitokauden-alkuvuosi 2019
                                                                                                                                                                                       :id                    8762
                                                                                                                                                                                       :suunniteltu_maara     40
                                                                                                                                                                                       :tehtava               "Pysäkkikatoksen uusiminen"
                                                                                                                                                                                       :toteuma_aika          nil
                                                                                                                                                                                       :toteutunut            5
                                                                                                                                                                                       :urakka                35
                                                                                                                                                                                       :yksikko               "kpl"}])
                                                                                                                                                 :toistettava-osa        (fn [vektori]
                                                                                                                                                                           (mapv
                                                                                                                                                                             (fn [{:keys [id hoitokauden-alkuvuosi suunniteltu_maara tehtava toteutunut yksikko]}]
                                                                                                                                                                               (with-meta
                                                                                                                                                                                 (new-grid/rivi {:koko {:seuraa {:seurattava ::otsikko
                                                                                                                                                                                                                 :sarakkeet  :sama
                                                                                                                                                                                                                 :rivit      :sama}}
                                                                                                                                                                                                 :osat [(with-meta
                                                                                                                                                                                                          #_(solu/linkki {:parametrit {:class #{"table-default"}}
                                                                                                                                                                                                                          :fmt        aika-tekstilla-fmt})
                                                                                                                                                                                                          (solu/teksti)
                                                                                                                                                                                                          {:key (str rivi "-" id "-otsikko")})
                                                                                                                                                                                                        (with-meta
                                                                                                                                                                                                          (solu/teksti)
                                                                                                                                                                                                          {:key (str rivi "-" id "-maara")})
                                                                                                                                                                                                        (with-meta
                                                                                                                                                                                                          (solu/tyhja)
                                                                                                                                                                                                          {:key (str rivi "-" id "-toteuma")})
                                                                                                                                                                                                        (with-meta
                                                                                                                                                                                                          (solu/tyhja)
                                                                                                                                                                                                          {:key (str rivi "-" id "-prosentti")})]}
                                                                                                                                                                                                [{:sarakkeet [0 4] :rivit [0 1]}])
                                                                                                                                                                                 {:key (str rivi "-" id)}))
                                                                                                                                                                             vektori))})
                                                                                                                         {:key (str rivi "-data-sisalto")}
                                                                                                                         )
                                                                                                                       ]
                                                                                                              }
                                                                                                             )
                                                                                              {:key rivi}
                                                                                              )
                                                                                            )
                                                                                          vectori
                                                                                          ))
                                                          }
                                                         )
                                  ]}))