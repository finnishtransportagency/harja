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
            [harja.ui.viesti :as viesti]
            [harja.ui.taulukko.grid :as new-grid]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.tiedot.navigaatio :as nav])

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
(defrecord HaeToteutuneetMaarat [urakka-id toimenpide hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm])
(defrecord ToteutuneetMaaratHakuOnnistui [vastaus])
(defrecord ToteutuneetMaaratHakuEpaonnistui [vastaus])
(defrecord AvaaRivi [avain])
(defrecord MuokkaaToteumaa [toteuma-id])
(defrecord ToteumaHakuOnnistui [vastaus])
(defrecord ToteumaHakuEpaonnistui [vastaus])
(defrecord ToteumanSyotto [auki tehtava toimenpide])
(defrecord AsetaLoppuPvm [arvo])
(defrecord AsetaMaara [maara])
(defrecord AsetaLisatieto [arvo])
(defrecord PoistaToteuma [id])
(defrecord TallennaToteuma [])
(defrecord TallennaToteumaOnnistui [vastaus])
(defrecord TallennaToteumaEpaonnistui [vastaus])
(defrecord TehtavatHakuOnnistui [vastaus])
(defrecord TehtavatHakuEpaonnistui [vastaus])

(def tyyppi->tyyppi
  {"kokonaishintainen"     "maaramitattava"
   "muut-rahavaraukset"    "tilaajan-varaukset"
   "lisatyo"               "lisatyo"
   "vahinkojen-korjaukset" "vahinkojen-korjaukset"
   "akillinen-hoitotyo"    "akillinen-hoitotyo"})

(extend-protocol tuck/Event

  ValitseToimenpide
  (process-event [{urakka :urakka toimenpide :toimenpide} app]
    (do
      (js/console.log "ValitseToimenpide" (pr-str toimenpide))
      (hae-toteutuneet-maarat urakka toimenpide
                              (get-in app [:toteuma :vuosi])
                              (get-in app [:toteuma :aikavali-alkupvm])
                              (get-in app [:toteuma :aikavali-loppupvm]))
      (hae-tehtavat toimenpide)
      (-> app
          (assoc-in [:toteuma :toimenpide] toimenpide)
          (assoc-in [:toteuma :tehtava] nil)
          (validoi-lomake))))

  ValitseTehtava
  (process-event [{tehtava :tehtava} app]
    (do
      (js/console.log "ValitseTehtava" (pr-str tehtava))
      (-> app
          (assoc-in [:toteuma :tehtava] tehtava)
          (validoi-lomake))))

  AsetaMaara
  (process-event [{maara :maara} app]
    (do
      (js/console.log "AsetaMaara" (pr-str maara))
      (-> app
          (assoc-in [:toteuma :maara] maara)
          (validoi-lomake))))

  AsetaLisatieto
  (process-event [{arvo :arvo} app]
    (do
      (js/console.log "AsetaLisatieto" (pr-str arvo))
      (-> apps
          (assoc-in [:toteuma :lisatieto] arvo)
          (validoi-lomake))))

  AsetaLoppuPvm
  (process-event [{arvo :arvo} app]
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
    (let [_ (js/console.log "ToteumaHakuOnnistui :: vastaus" (pr-str vastaus))
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
      (hae-toteutuneet-maarat urakka (get-in app [:toteuma :toimenpide]) vuosi nil nil)
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
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc-in [:toteutuneet-maarat] vastaus)
        (assoc-in [:toteutuneet-maarat-grouped] (group-by :tehtava vastaus))))

  ToteutuneetMaaratHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    (js/console.log "ToteutuneetMaaratHakuEpaonnistui :: vastaus" (pr-str vastaus))
    app)

  TehtavatHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc-in [:tehtavat] vastaus)
        (validoi-lomake)))

  TehtavatHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    (js/console.log "TehtavatHakuEpaonnistui :: vastaus" (pr-str vastaus))
    app)

  ToteumanSyotto
  (process-event [{auki :auki tehtava :tehtava toimenpide :toimenpide} app]
    (js/console.log "ToteumanSyotto " (pr-str auki) (pr-str tehtava) (pr-str toimenpide))
    (cond-> app
            (not (nil? tehtava)) (assoc-in [:toteuma :tehtava] tehtava)
            (not (nil? toimenpide)) (assoc-in [:toteuma :toimenpide] toimenpide)
            true (assoc-in [:syottomoodi] auki)
            true (assoc-in [:toteuma :vuosi] nil)
            true (assoc-in [:toteuma :toteuma-id] nil)
            true (assoc-in [:toteuma :toteuma-tehtava-id] nil)
            true (assoc-in [:toteuma :lisatieto] nil)
            true (assoc-in [:toteuma :maara] nil)
            true (assoc-in [:toteuma :loppupvm] (pvm/nyt))))

  PoistaToteuma
  (process-event [{id :id} app]
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
                            (get-in app [:toteuma :toimenpide])
                            (get-in app [:hoitokauden-alkuvuosi])
                            (get-in app [:aikavali-alkupvm])
                            (get-in app [:aikavali-loppupvm]))
    app)

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
                     (not (nil? (get-in app [:toteuma :toimenpide])))
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