(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake
  (:require [reagent.core :refer [atom] :as r]
            [clojure.data :refer [diff]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.domain.paikkaus :as paikkaus]
            [harja.ui.modal :as modal]
            [harja.ui.viesti :as viesti]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.urakka :as tila])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn paikkauksen-yksikon-maara-avain [yksikko]
  (cond
    (= "t" yksikko) :massamaara
    (= "m2" yksikko) :pinta-ala
    (= "jm" yksikko) :juoksumetri
    (= "kpl" yksikko) :kpl
    :else :massamaara))

(defrecord AvaaToteumaLomake [toteumalomake paikkauskohde])
(defrecord SuljeToteumaLomake [])
(defrecord KenttaanKoskettu [kentan-nimi arvo])
(defrecord PaivitaLomake [toteumalomake lomake-tiedot])
(defrecord TallennaToteuma [toteuma])
(defrecord TallennaToteumaOnnistui [toteuma muokattu])
(defrecord TallennaToteumaEpaonnistui [toteuma muokattu])
(defrecord PoistaToteuma [toteuma])
(defrecord PoistaToteumaOnnistui [toteuma])
(defrecord PoistaToteumaEpaonnistui [toteuma])

(defn- siivoa-ennen-lahetysta [lomake]
  (-> lomake
      (assoc :urakka-id (-> @tila/yleiset :urakka :id))
      (update :ajorata (fn [nykyinen-arvo]
                         (if (= "Ei ajorataa" nykyinen-arvo)
                           nil
                           nykyinen-arvo)))
      (dissoc :sijainti
              :pituus
              :paikkauskohde-nimi
              :paikkauskohde-tila
              :toteumien-maara
              :tyyppi
              :harja.tiedot.urakka.urakka/koske
              :harja.tiedot.urakka.urakka/validi?
              :harja.tiedot.urakka.urakka/validius
              :harja.tiedot.urakka.urakka/validoi
              :harja.tiedot.urakka.urakka/kutsu-jos-onnistui-fn
              :kohteen-yksikko)))

(defn- tallenna-toteuma [toteuma onnistui epaonnistui]
  (let [toteuma (siivoa-ennen-lahetysta toteuma)]
    (tuck-apurit/post! :tallenna-kasinsyotetty-paikkaus
                          toteuma
                          {:onnistui onnistui
                           ;:onnistui-parametrit parametrit
                           :epaonnistui epaonnistui
                           ;:epaonnistui-parametrit parametrit
                           :paasta-virhe-lapi? true})
    toteuma))

;; Nämä validointi funktiot ajetaan vain kerran ja sinne annettu toteumalomake ei päivity.
;; Joten siirrytään käyttämään atomia, jossa on ajantasainen toteumalomake
(def lomake-atom (atom {}))
(defn validoinnit [avain]
  ;; Toteumalomakkeita on kaksi erilaista ja siitä syystä tehdään täysin erilaiset validoinnit
  (avain
    ;; Nämä kentät ovat yhteisiä molemmille lomakkeille
    (merge {:tie [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
            :aosa [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
            :losa [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
            :aet [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
            :let [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)
                  (tila/silloin-kun #(not (nil? (:aet @lomake-atom)))
                                    (fn [arvo]
                                      ;; Validointi vaatii "nil" vastauksen, kun homma on pielessä ja kentän arvon, kun kaikki on ok
                                      (cond
                                        ;; Jos alkuosa ja loppuosa on sama
                                        ;; Ja alkuetäisyys on pienempi kuin loppuetäisyys)
                                        (and (= (:aosa @lomake-atom) (:losa @lomake-atom)) (< (:aet @lomake-atom) arvo))
                                        arvo
                                        ;; Jos alkuetäisyys on sama tai suurempi kuin loppu
                                        (and (= (:aosa @lomake-atom) (:losa @lomake-atom)) (>= (:aet @lomake-atom) arvo))
                                        nil
                                        :else arvo)))]
            :alkuaika [tila/ei-nil tila/ei-tyhja tila/paivamaara]
            :loppuaika [tila/ei-nil tila/ei-tyhja tila/paivamaara
                        (tila/silloin-kun #(and (not (nil? (:alkuaika @lomake-atom))) (not (nil? (:loppuaika @lomake-atom))))
                                          (fn [arvo]
                                            ;; Validointi vaatii "nil" vastauksen, kun homma on pielessä ja kentän arvon, kun kaikki on ok
                                            (when (or (pvm/sama-pvm? (:alkuaika @lomake-atom) arvo) ;; Joko sama päivä
                                                      (pvm/ennen? (:alkuaika @lomake-atom) arvo)) ;; Tai alkupäivämäärä tulee ennen loppupäivää
                                              arvo)))]}

           (if (paikkaus/levittimella-tehty? @lomake-atom (get-in @tila/paikkauskohteet [:valinnat :tyomenetelmat]))
             {:ajorata [tila/ei-nil tila/ei-tyhja tila/numero]
              :kaista [tila/ei-nil tila/ei-tyhja tila/numero]
              :massatyyppi [tila/ei-nil tila/ei-tyhja]
              :raekoko [tila/ei-nil tila/ei-tyhja]
              :kuulamylly [tila/ei-nil tila/ei-tyhja]
              :massamenekki [tila/ei-nil tila/ei-tyhja tila/numero]
              :massamaara [tila/ei-nil tila/ei-tyhja tila/numero]
              :leveys [tila/ei-nil tila/ei-tyhja tila/numero]
              :pinta-ala [tila/ei-nil tila/ei-tyhja tila/numero]}
             {(paikkauksen-yksikon-maara-avain (:kohteen-yksikko @lomake-atom)) [tila/ei-nil tila/ei-tyhja tila/numero]}))))

(defn- validoi-lomake [toteumalomake]
  (apply tila/luo-validius-tarkistukset
         (if (paikkaus/levittimella-tehty? toteumalomake (get-in @tila/paikkauskohteet [:valinnat :tyomenetelmat]))
           ;; Levittäjälle erilaiset validoinnit
           [[:tie] (validoinnit :tie)
            [:ajorata] (validoinnit :ajorata)
            [:kaista] (validoinnit :kaista)
            [:aosa] (validoinnit :aosa)
            [:losa] (validoinnit :losa)
            [:aet] (validoinnit :aet)
            [:let] (validoinnit :let)
            [:alkuaika] (validoinnit :alkuaika)
            [:loppuaika] (validoinnit :loppuaika)
            [:massatyyppi] (validoinnit :massatyyppi)
            [:raekoko] (validoinnit :raekoko)
            [:kuulamylly] (validoinnit :kuulamylly)
            [:massamenekki] (validoinnit :massamenekki)
            [:massamaara] (validoinnit :massamaara)
            [:leveys] (validoinnit :leveys)
            [:pinta-ala] (validoinnit :pinta-ala)]

           [[:tie] (validoinnit :tie)
            [:aosa] (validoinnit :aosa)
            [:losa] (validoinnit :losa)
            [:aet] (validoinnit :aet)
            [:let] (validoinnit :let)
            [:alkuaika] (validoinnit :alkuaika)
            [:loppuaika] (validoinnit :loppuaika)
            [(paikkauksen-yksikon-maara-avain (:kohteen-yksikko toteumalomake))]
            (validoinnit (paikkauksen-yksikon-maara-avain (:kohteen-yksikko toteumalomake)))])))

(extend-protocol tuck/Event

  AvaaToteumaLomake
  (process-event [{toteumalomake :toteumalomake paikkauskohde :paikkauskohde} app]
    (let [;; Esitäytetään toteumalomakkeelle asioita paikkauskohteelta, jos se on annettu
          toteumalomake (if paikkauskohde
                          (-> toteumalomake
                              (assoc :alkuaika (:alkupvm paikkauskohde))
                              (assoc :loppuaika (:loppupvm paikkauskohde))
                              (assoc :tie (:tie paikkauskohde))
                              (assoc :ajorata (:ajorata paikkauskohde))
                              (assoc :aosa (:aosa paikkauskohde))
                              (assoc :losa (:losa paikkauskohde))
                              (assoc :kohteen-yksikko (:yksikko paikkauskohde)))
                          toteumalomake)
          _ (reset! lomake-atom toteumalomake)
          {:keys [validoi koske] :as validoinnit} (validoi-lomake toteumalomake)
          {:keys [validi? validius]} (validoi validoinnit toteumalomake)]
      (-> app
          (assoc :toteumalomake toteumalomake)
          ;(assoc-in [:toteumalomake ::tila/validoi] validoi)
          ;(assoc-in [:toteumalomake ::tila/koske] koske)
          (assoc-in [:toteumalomake ::tila/validius] validius)
          (assoc-in [:toteumalomake ::tila/validi?] validi?))))

  SuljeToteumaLomake
  (process-event [_ app]
    (dissoc app :toteumalomake))

  ;; Tällä hetkellä tälle ei ole käyttöä, joten palautetaan vain app-state
  KenttaanKoskettu
  (process-event [{:keys [kentan-nimi arvo]} 
                  {{::tila/keys [koske validius]} :toteumalomake :as app}]
    #_ (let [ignoratut-kentat #{:pituus}
          validius (if-not (contains? ignoratut-kentat kentan-nimi) 
                     (koske validius [kentan-nimi])
                     validius)]
      (assoc-in app [:toteumalomake ::tila/validius] validius))
    app)

  PaivitaLomake
  (process-event [{toteumalomake :toteumalomake 
                   {::lomake/keys [viimeksi-muokattu-kentta]} :lomake-tiedot} 
                  {{::tila/keys [validoi koske validius validi?]} :toteumalomake :as app}]
    (let [toteumalomake (t-paikkauskohteet/laske-paikkauskohteen-pituus toteumalomake [:toteumalomake])
          _ (reset! lomake-atom toteumalomake)
          {:keys [validoi koske] :as validoinnit} (validoi-lomake toteumalomake)
          {:keys [validi? validius]} (validoi validoinnit toteumalomake)]
      (-> app
          (assoc :toteumalomake toteumalomake)
          (assoc-in [:toteumalomake ::tila/validius] validius)
          (assoc-in [:toteumalomake ::tila/validi?] validi?))))

  TallennaToteuma
  (process-event [{toteuma :toteuma} app]
    (let [toteuma (-> toteuma
                      (assoc :urakka-id (-> @tila/tila :yleiset :urakka :id))
                      (dissoc ::paikkaus/ajourat ::paikkaus/reunat ::paikkaus/ajorata ::paikkaus/kaista
                              ::paikkaus/keskisaumat ::paikkaus/tienkohta-id ::paikkaus/ajouravalit))]
      (do
        (tallenna-toteuma toteuma
                          ->TallennaToteumaOnnistui
                          ->TallennaToteumaEpaonnistui)
        app)))

  TallennaToteumaOnnistui
  (process-event [{muokattu :muokattu toteuma :toteuma} {:keys [post-haku-paivitys-fn] :as app}]
    (let [_ (modal/piilota!)
          _ (t-paikkauskohteet/hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)]
      (when post-haku-paivitys-fn
        (post-haku-paivitys-fn toteuma))
      (viesti/nayta-toast! "Toteuma tallennettu")
      (-> app
          (assoc :toteuma-lisatty? true
                 :raportointi-tila? (get-in app [:lomake :tyyppi]))
          (dissoc :toteumalomake))))

  TallennaToteumaEpaonnistui
  (process-event [{muokattu :muokattu toteuma :toteuma} app]
    (do
      (if muokattu
        (viesti/nayta-toast! "Toteuman muokkaus epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton)
        (viesti/nayta-toast! "Toteuman tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton))
      app))

  PoistaToteuma
  (process-event [{toteuma :toteuma} app]
    (do
      (tuck-apurit/post! :poista-kasinsyotetty-paikkaus
                         (siivoa-ennen-lahetysta toteuma)
                         {:onnistui ->PoistaToteumaOnnistui
                          :epaonnistui ->PoistaToteumaEpaonnistui
                          :paasta-virhe-lapi? true})
      app))

  PoistaToteumaOnnistui
  (process-event [{toteuma :toteuma} {:keys [post-haku-paivitys-fn] :as app}]
    (let [_ (modal/piilota!)]
      (when post-haku-paivitys-fn
        (post-haku-paivitys-fn toteuma))
      (viesti/nayta-toast! (str "Toteuma poistettu"))
      (dissoc app :toteumalomake)))

  PoistaToteumaEpaonnistui
  (process-event [{toteuma :toteuma} app]
    (let [_ (modal/piilota!)]
      (viesti/nayta-toast! (str "Toteuman poistamisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :toteumalomake)))

  )
