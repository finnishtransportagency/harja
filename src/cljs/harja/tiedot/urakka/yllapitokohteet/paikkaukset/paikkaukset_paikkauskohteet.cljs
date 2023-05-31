(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [reagent.core :refer [atom]]
            [clojure.data :refer [diff]]
            [clojure.string :as str]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.ui.modal :as modal]
            [harja.ui.viesti :as viesti]
            [harja.ui.lomake :as lomake]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as paikkauskohteet-kartalle]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tr-domain])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def lomakkeen-pituuskentat (atom {:pituus nil :tie nil :aosa nil :aet nil :losa nil :let nil}))

(defn virhe-modal [virhe otsikko]
  (modal/nayta!
    {:otsikko otsikko
     :otsikko-tyyli :virhe}
    (when virhe
      (doall
        (for [rivi virhe]
          ^{:key (hash rivi)}
          [:div
           [:b (get rivi "paikkauskohde")]
           (if (vector? (get rivi "virhe"))
             (doall
               [:ul
                (for [v (get rivi "virhe")]
                  ^{:key (str (hash v) (hash rivi))}
                  [:li (str v)]
                  )])
             [:p (str (get rivi "virhe"))])
           ]))
      )))

(defn lomakkeen-hash [lomake]
  (as-> lomake vertailtava-lomake
        (lomake/ilman-lomaketietoja lomake)
        (dissoc vertailtava-lomake ::tila/validius ::tila/validi? :tyyppi :alku-hash)
        (assoc vertailtava-lomake :alkupvm (str (:alkupvm vertailtava-lomake)))
        (assoc vertailtava-lomake :loppupvm (str (:loppupvm vertailtava-lomake)))
        (hash vertailtava-lomake)))

(defn- fmt-aikataulu
  "Formatoi paikkauskohteen aikataulun."
  [{:keys [alkupvm loppupvm valmistumispvm paikkauskohteen-tila]}]
  (str
    (pvm/fmt-paiva-ja-kuukausi-lyhyt alkupvm)
    " - "
    ;; Loppupäiväksi valitaan valmistumispvm jos saatavilla, muuten näytetään arvio (loppupvm).
    (pvm/fmt-p-k-v-lyhyt (or valmistumispvm loppupvm))
    (when-not (= "valmis" paikkauskohteen-tila)
      " (arv.)")))

(defn- fmt-valmistuminen
  "Tiemerkkarille näytetään arvioitu valmistumispäivämäärä"
  [loppupvm]
  (str (pvm/pvm-opt loppupvm) " (arv.) "))


(defrecord PaivitaTiemerkintaModal [tiemerkintalomake])
(defrecord SuljeTiemerkintaModal [])
(defrecord AvaaTiemerkintaModal [tiemerkintalomake])
(defrecord AvaaTiemerkintaModalOnnistui [vastaus])
(defrecord AvaaTiemerkintaModalEpaonnistui [vastaus])
(defrecord MerkitsePaikkauskohdeValmiiksiOnnistui [vastaus])
(defrecord MerkitsePaikkauskohdeValmiiksiEpaonnistui [vastaus])
(defrecord AvaaLomake [lomake])
(defrecord SuljeLomake [])

(defrecord FiltteriValitseTila [uusi-tila valittu?])
(defrecord FiltteriValitseVuosi [uusi-vuosi])
(defrecord FiltteriValitseTyomenetelma [uusi-menetelma valittu?])
(defrecord FiltteriValitseEly [uusi-ely valittu?])
(defrecord TiedostoLadattu [vastaus])
(defrecord HaePaikkauskohteet [])
(defrecord HaePaikkauskohteetOnnistui [vastaus])
(defrecord HaePaikkauskohteetEpaonnistui [vastaus])
(defrecord PaivitaLomake [lomake])
(defrecord TallennaPaikkauskohdeRaportointitilassa [paikkauskohde])
(defrecord TallennaPaikkauskohde [paikkauskohde])
(defrecord TallennaPaikkauskohdeOnnistui [paikkauskohde muokattu])
(defrecord TallennaPaikkauskohdeEpaonnistui [paikkauskohde muokattu])
(defrecord TilaaPaikkauskohdeOnnistui [vastaus])
(defrecord TilaaPaikkauskohdeEpaonnistui [vastaus])
(defrecord HylkaaPaikkauskohdeOnnistui [paikkauskohde])
(defrecord HylkaaPaikkauskohdeEpaonnistui [paikkauskohde])
(defrecord PoistaPaikkauskohde [paikkauskohde])
(defrecord PoistaPaikkauskohdeOnnistui [paikkauskohde])
(defrecord PoistaPaikkauskohdeEpaonnistui [paikkauskohde])
(defrecord PeruPaikkauskohteenTilausOnnistui [vastaus])
(defrecord PeruPaikkauskohteenTilausEpaonnistui [vastaus])
(defrecord PeruPaikkauskohteenHylkaysOnnistui [vastaus])
(defrecord PeruPaikkauskohteenHylkaysEpaonnistui [paikkauskohde])
(defrecord LaskePituusOnnistui [vastaus lomakeavain])
(defrecord LaskePituusEpaonnistui [vastaus])
(defrecord JarjestaPaikkauskohteet [jarjestys])
(defrecord AsetaToteumatyyppi [uusi-tyyppi])

(defn- tilat-hakuun [tilat]
  (let [sql-tilat {"Kaikki" "kaikki",
                   "Ehdotettu" "ehdotettu",
                   "Hylätty" "hylatty",
                   "Tilattu" "tilattu",
                   "Valmis" "valmis",
                   "Tarkistettu" "tarkistettu"}
        tilat (set (mapv #(sql-tilat %) tilat))]
    tilat))

(defn- siivoa-ennen-lahetysta [lomake]
  (-> lomake
      (update :ajorata (fn [nykyinen-arvo]
                         (if (= "Ei ajorataa" nykyinen-arvo)
                           nil
                           nykyinen-arvo)))
      (dissoc :sijainti
              :pituus
              :harja.tiedot.urakka.urakka/validi?
              :harja.tiedot.urakka.urakka/validius
              :valiaika-takuuaika
              :valiaika-valmistumispvm
              :paikkaustyo-valmis?
              :erotus
              :toteutus-alkuaika
              :toteutus-loppuaika)))

(defn laske-paikkauskohteen-pituus [lomake parametrit]
  (let [;; Tarkistetaan ensin, että onko pituuskentät muuttuneet, jos ei niin ei lasketa pituutta
        nykyiset-pituuskentat {:pituus (:pituus lomake)
                               :tie (:tie lomake)
                               :aosa (:aosa lomake)
                               :aet (:aet lomake)
                               :losa (:losa lomake)
                               :let (:let lomake)}
        pituus-diff (diff @lomakkeen-pituuskentat nykyiset-pituuskentat)
        _ (when (and ;; Tarkistetaan, että kaikki kentät on annettu
                  (not (nil? (:tie lomake)))
                  (not (nil? (:aosa lomake)))
                  (not (nil? (:aet lomake)))
                  (not (nil? (:losa lomake)))
                  (not (nil? (:let lomake)))
                  ;; Ja pituus on muuttunut
                  (or (not (nil? (first pituus-diff)))
                      (not (nil? (second pituus-diff)))))
            (do
              (reset! lomakkeen-pituuskentat nykyiset-pituuskentat)
              (tuck-apurit/post! :laske-paikkauskohteen-pituus
                                 nykyiset-pituuskentat
                                 {:onnistui ->LaskePituusOnnistui
                                  :onnistui-parametrit parametrit
                                  :epaonnistui ->LaskePituusEpaonnistui
                                  :paasta-virhe-lapi? true})))]
    lomake))

(defn hae-paikkauskohteet [urakka-id {:keys [valitut-tilat valittu-vuosi valitut-tyomenetelmat valitut-elyt hae-aluekohtaiset-paikkauskohteet?] :as app}]
  (let [alkupvm (pvm/->pvm (str "1.1." valittu-vuosi))
        loppupvm (pvm/->pvm (str "31.12." valittu-vuosi))]
    (tuck-apurit/post! :paikkauskohteet-urakalle
                       {:urakka-id urakka-id
                        :tilat (tilat-hakuun valitut-tilat)
                        :alkupvm alkupvm
                        :loppupvm loppupvm
                        :tyomenetelmat valitut-tyomenetelmat
                        :elyt valitut-elyt
                        :hae-alueen-kohteet? hae-aluekohtaiset-paikkauskohteet?}
                       {:onnistui ->HaePaikkauskohteetOnnistui
                        :epaonnistui ->HaePaikkauskohteetEpaonnistui
                        :paasta-virhe-lapi? true})
    (assoc app :haku-kaynnissa? true)))

(defn paikkauskohde-id->nimi [app id]
  (:name (first (filter #(= id (:id %)) (:paikkauskohteet app)))))

(defn tallenna-tilamuutos! [paikkauskohde]
  (let [paikkauskohde (-> paikkauskohde
                        (assoc :valmistumispvm (or (:valiaika-valmistumispvm paikkauskohde) (:valmistumispvm paikkauskohde)))
                        (assoc :takuuaika (or (:valiaika-takuuaika paikkauskohde) (:takuuaika paikkauskohde))))
        paikkauskohde (siivoa-ennen-lahetysta paikkauskohde)]
    (k/post! :tallenna-paikkauskohde-urakalle
             paikkauskohde)))

(defn- tallenna-paikkauskohde
  ([paikkauskohde onnistui epaonnistui]
   (tallenna-paikkauskohde paikkauskohde onnistui epaonnistui nil))
  ([paikkauskohde onnistui epaonnistui parametrit]
   (let [paikkauskohde (siivoa-ennen-lahetysta paikkauskohde)]
     (tuck-apurit/post! :tallenna-paikkauskohde-urakalle
                        paikkauskohde
                        {:onnistui onnistui
                         :onnistui-parametrit parametrit
                         :epaonnistui epaonnistui
                         :epaonnistui-parametrit parametrit
                         :paasta-virhe-lapi? true}))))

;; Nämä validointi funktiot ajetaan vain kerran ja sinne annettu toteumalomake ei päivity.
;; Joten siirrytään käyttämään atomia, jossa on ajantasainen toteumalomake
(def lomake-atom (atom {}))
(defn validoinnit [avain]
  (avain
    (merge
      {:nimi [tila/ei-nil tila/ei-tyhja]
       :ulkoinen-id [tila/ei-nil tila/ei-tyhja tila/numero]
       :tyomenetelma [tila/ei-nil tila/ei-tyhja]
       :tie [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
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
                                   ;; Alkuetäisyys on suurempi kuin loppuetäisyys
                                   (and (= (:aosa @lomake-atom) (:losa @lomake-atom)) (>= (:aet @lomake-atom) arvo))
                                   nil
                                   :else arvo)))]
       :alkupvm [tila/ei-nil tila/ei-tyhja tila/paivamaara]
       :loppupvm [tila/ei-nil tila/ei-tyhja tila/paivamaara
                  (tila/silloin-kun #(not (nil? (:alkupvm @lomake-atom)))
                                    (fn [arvo]
                                      ;; Validointi vaatii "nil" vastauksen, kun homma on pielessä ja kentän arvon, kun kaikki on ok
                                      (when (or (pvm/sama-pvm? (:alkupvm @lomake-atom) arvo) ;; Joko sama päivä
                                                (pvm/ennen? (:alkupvm @lomake-atom) arvo)) ;; Tai alkupäivämäärä tulee ennen loppupäivää
                                        arvo)))]
       :suunniteltu-maara [tila/ei-nil tila/ei-tyhja tila/numero]
       :yksikko [tila/ei-nil tila/ei-tyhja]
       :suunniteltu-hinta [tila/ei-nil tila/ei-tyhja tila/numero]
       }
      ;; Pot raportoitavalla lomakkeella on lisäksi vielä pari kenttää
      (when (= :pot (:toteumatyyppi @lomake-atom))
        {:pot-tyo-alkoi [tila/ei-nil tila/ei-tyhja tila/paivamaara]
         :pot-tyo-paattyi [tila/ei-nil tila/ei-tyhja tila/paivamaara
                           (tila/silloin-kun #(not (nil? (:pot-tyo-alkoi @lomake-atom)))
                             (fn [arvo]
                               ;; Validointi vaatii "nil" vastauksen, kun homma on pielessä ja kentän arvon, kun kaikki on ok
                               (when (or (pvm/sama-pvm? (:pot-tyo-alkoi @lomake-atom) arvo) ;; Joko sama päivä
                                       (pvm/ennen? (:pot-tyo-alkoi @lomake-atom) arvo)) ;; Tai alkupäivämäärä tulee ennen loppupäivää
                                 arvo)))]
         :pot-valmistumispvm [tila/ei-nil tila/ei-tyhja tila/paivamaara
                              (tila/silloin-kun #(and (not (nil? (:pot-valmistumispvm @lomake-atom))) (not (nil? (:pot-tyo-paattyi @lomake-atom))))
                                (fn [arvo]
                                  ;; Validointi vaatii "nil" vastauksen, kun homma on pielessä ja kentän arvon, kun kaikki on ok
                                  (when (or (pvm/sama-pvm? (:pot-tyo-paattyi @lomake-atom) arvo) ;; Joko sama päivä
                                          (pvm/ennen? (:pot-tyo-paattyi @lomake-atom) arvo) ;; Tai alkupäivämäärä tulee ennen valmistumista
                                          (nil? arvo)) ;; Nil on ihan ok vaihtoehto tässä vaiheessa
                                    arvo)))]})
      )))

(defn- validoi-lomake [lomake]
  (apply tila/luo-validius-tarkistukset
         (if-not (= :pot (:toteumatyyppi @lomake-atom))
           [[:nimi] (validoinnit :nimi)
            [:ulkoinen-id] (validoinnit :ulkoinen-id)
            [:tyomenetelma] (validoinnit :tyomenetelma)
            [:tie] (validoinnit :tie)
            [:aosa] (validoinnit :aosa)
            [:losa] (validoinnit :losa)
            [:aet] (validoinnit :aet)
            [:let] (validoinnit :let)
            [:alkupvm] (validoinnit :alkupvm)
            [:loppupvm] (validoinnit :loppupvm)
            [:suunniteltu-maara] (validoinnit :suunniteltu-maara)
            [:yksikko] (validoinnit :yksikko)
            [:suunniteltu-hinta] (validoinnit :suunniteltu-hinta)]
           ;; Pot raportoitavalla on erilaiset  validoinnit
           [[:nimi] (validoinnit :nimi)
            [:ulkoinen-id] (validoinnit :ulkoinen-id)
            [:tyomenetelma] (validoinnit :tyomenetelma)
            [:tie] (validoinnit :tie)
            [:aosa] (validoinnit :aosa)
            [:losa] (validoinnit :losa)
            [:aet] (validoinnit :aet)
            [:let] (validoinnit :let)
            [:alkupvm] (validoinnit :alkupvm)
            [:loppupvm] (validoinnit :loppupvm)
            [:suunniteltu-maara] (validoinnit :suunniteltu-maara)
            [:yksikko] (validoinnit :yksikko)
            [:suunniteltu-hinta] (validoinnit :suunniteltu-hinta)
            [:pot-tyo-alkoi] (validoinnit :pot-tyo-alkoi)
            [:pot-tyo-paattyi] (validoinnit :pot-tyo-paattyi)
            [:pot-valmistumispvm] (validoinnit :pot-valmistumispvm)]
           )))

(defn- validoi-tiemerkintamodal-lomake [lomake]
  (apply tila/luo-validius-tarkistukset [[:tiemerkinta-urakka] (validoinnit :tiemerkinta-urakakka)
                                         [:viesti] (validoinnit :viesti)]))

(defn- kaanteinen-jarjestaja [a b]
  (compare b a))

(extend-protocol tuck/Event

  PaivitaTiemerkintaModal
  (process-event [{tiemerkintalomake :tiemerkintalomake} app]
    (let [{:keys [validoi] :as validoinnit} (validoi-tiemerkintamodal-lomake tiemerkintalomake)
          {:keys [validi? validius]} (validoi validoinnit tiemerkintalomake)]
      (-> app
          (assoc :tiemerkintalomake tiemerkintalomake)
          (assoc-in [:tiemerkintalomake ::tila/validius] validius)
          (assoc-in [:tiemerkintalomake ::tila/validi?] validi?))))

  AvaaTiemerkintaModal
  (process-event [{tiemerkintalomake :tiemerkintalomake} app]
    (let [{:keys [validoi] :as validoinnit} (validoi-tiemerkintamodal-lomake tiemerkintalomake)
          {:keys [validi? validius]} (validoi validoinnit tiemerkintalomake)]
      ;; Tiemerkintämodallissa tarvitaan tiemerkintäurakoista tiedot, joten avataan modaali
      ;; sen jälkeen, kun tiedot on haettu serveriltä
      (do
        (tuck-apurit/post! :hae-paikkauskohteen-tiemerkintaurakat
                           {:urakka-id (-> @tila/yleiset :urakka :id)}
                           {:onnistui ->AvaaTiemerkintaModalOnnistui
                            :epaonnistui ->AvaaTiemerkintaModalEpaonnistui
                            :paasta-virhe-lapi? true})
        (-> app
            (assoc :tiemerkintalomake tiemerkintalomake)
            (assoc-in [:tiemerkintalomake ::tila/validius] validius)
            (assoc-in [:tiemerkintalomake ::tila/validi?] validi?)))))

  AvaaTiemerkintaModalOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc-in [:tiemerkintalomake :tiemerkinta-urakka] (:id (first vastaus)))
        (assoc :tiemerkintaurakat vastaus)
        (assoc-in [:lomake :tiemerkintamodal] true)))

  AvaaTiemerkintaModalEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Tiemerkintäurakoiden haku epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton)
      (assoc-in app [:lomake :tiemerkintamodal] false)))

  SuljeTiemerkintaModal
  (process-event [_ app]
    (-> app
        (dissoc app :tiemerkintalomake)
        (assoc-in [:lomake :tiemerkintamodal] false)))

  MerkitsePaikkauskohdeValmiiksiOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast!
        (str "Paikkauskohde " (paikkauskohde-id->nimi app (:paikkauskohde-id vastaus)) " merkitty valmiiksi"))
      (dissoc app :lomake :tiemerkintalomake)))

  MerkitsePaikkauskohdeValmiiksiEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:paikkauskohde-id vastaus)) " valmiiksi merkitsemisessä tapahtui virhe!")
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :lomake :tiemerkintalomake)))

  AvaaLomake
  (process-event [{lomake :lomake} app]
    (let [_ (reset! lomake-atom lomake)
          {:keys [validoi] :as validoinnit} (validoi-lomake lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)]
      (-> app
          (dissoc :pmr-lomake)
          (dissoc :toteumalomake)
          (assoc :lomake lomake)
          (assoc-in [:lomake :alku-hash] (lomakkeen-hash lomake))
          (assoc-in [:lomake ::tila/validius] validius)
          (assoc-in [:lomake ::tila/validi?] validi?))))

  SuljeLomake
  (process-event [_ app]
    (dissoc app :lomake))

  FiltteriValitseTila
  (process-event [{uusi-tila :uusi-tila valittu? :valittu?} app]
    (let [valitut-tilat (:valitut-tilat app)
          tilat (cond
                  ;; Valitaan joku muu kuin "kaikki"
                  (and valittu? (not= "Kaikki" (:nimi uusi-tila)))
                  (-> valitut-tilat
                      (conj (:nimi uusi-tila))
                      (disj "Kaikki"))

                  ;; Valitaan "kaikki"
                  (and valittu? (= "Kaikki" (:nimi uusi-tila)))
                  #{"Kaikki"} ;; Palautetaan kaikki valinnalla

                  ;; Poistetaan "kaikki" valinta
                  (and (not valittu?) (= "Kaikki" (:nimi uusi-tila)))
                  (disj valitut-tilat "Kaikki")

                  ;; Poistetaan joku muu kuin "kaikki" valinta
                  (and (not valittu?) (not= "Kaikki" (:nimi uusi-tila)))
                  (disj valitut-tilat (:nimi uusi-tila)))] 
      (assoc app :valitut-tilat tilat)))

  FiltteriValitseVuosi
  (process-event [{uusi-vuosi :uusi-vuosi} app]
    (assoc app :valittu-vuosi uusi-vuosi))

  FiltteriValitseTyomenetelma
  (process-event [{uusi-menetelma :uusi-menetelma valittu? :valittu?} app]
    (let [valitut-tyomenetelmat (:valitut-tyomenetelmat app)
          menetelmat (cond
                       ;; Valitaan joku muu kuin "kaikki"
                       (and valittu? (not= "Kaikki" (:nimi uusi-menetelma)))
                       (-> valitut-tyomenetelmat
                           (conj (:id uusi-menetelma))
                           (disj "Kaikki"))

                       ;; Valitaan "kaikki"
                       (and valittu? (= "Kaikki" (:nimi uusi-menetelma)))
                       #{"Kaikki"} ;; Palautetaan kaikki valinnalla

                       ;; Poistetaan "kaikki" valinta
                       (and (not valittu?) (= "Kaikki" (:nimi uusi-menetelma)))
                       (disj valitut-tyomenetelmat "Kaikki")

                       ;; Poistetaan joku muu kuin "kaikki" valinta
                       (and (not valittu?) (not= "Kaikki" (:nimi uusi-menetelma)))
                       (disj valitut-tyomenetelmat (:id uusi-menetelma)))] 
      (assoc app :valitut-tyomenetelmat menetelmat)))

  FiltteriValitseEly
  (process-event [{uusi-ely :uusi-ely valittu? :valittu?} app]
    (let [valitut-elyt (:valitut-elyt app)
          elyt (cond
                 ;; Valitaan joku muu kuin "kaikki"
                 (and valittu? (not= 0 (:id uusi-ely)))
                 (-> valitut-elyt
                     (conj (:id uusi-ely))
                     (disj 0))

                 ;; Valitaan "kaikki"
                 (and valittu? (= 0 (:id uusi-ely)))
                 #{0} ;; Palautetaan kaikki valinnalla

                 ;; Poistetaan "kaikki" valinta
                 (and (not valittu?) (= 0 (:id uusi-ely)))
                 (disj valitut-elyt 0)

                 ;; Poistetaan joku muu kuin "kaikki" valinta
                 (and (not valittu?) (not= 0 (:id uusi-ely)))
                 (disj valitut-elyt (:id uusi-ely)))] 
      (assoc app :valitut-elyt elyt)))

  TiedostoLadattu
  (process-event [{vastaus :vastaus} app]
    (do
      ;; Excelissä voi mahdollisesti olla virheitä, jos näin on, niin avataan modaali, johon virheet kirjoitetaan
      ;; Jos taas kaikki sujui kuten Strömssössä, niin näytetään onnistumistoasti
      (cond 
        (and (not (nil? (:status vastaus)))
               (not= 200 (:status vastaus)))
        (do
          (viesti/nayta-toast! "Ladatun tiedoston käsittelyssä virhe"
                               :varoitus viesti/viestin-nayttoaika-lyhyt)
          (virhe-modal (conj (get-in vastaus [:response "virheet"]) "Huom. Voit ladata valmiin Excel-pohjan Lataa Excel-pohja -linkistä") "Virhe ladattaessa kohteita tiedostosta")
          (assoc app :excel-virhe (get-in vastaus [:response "virheet"])))
        ;; osa meni läpi, osa ei. näytetään virhemodaali vähän eri viestillä
        (and (nil? (:status vastaus))
             (> (count (get vastaus "virheet")) 0))
        (do 
          (viesti/nayta-toast! "Osassa paikkauskohteita virheitä, osa tallennettu onnistuneesti"
                               :varoitus viesti/viestin-nayttoaika-lyhyt)
          (virhe-modal (conj (get vastaus "virheet") "Huom. Voit ladata valmiin Excel-pohjan Lataa Excel-pohja -linkistä") "Osassa kohteita virheitä, osa tallennettu")
          (-> (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
              (assoc :excel-virhe (get vastaus "virheet"))))
        ;; kaikki ok
        :else
        (do
          ;; Ladataan uudet paikkauskohteet
          (viesti/nayta-toast! "Paikkauskohteet ladattu onnistuneesti"
                               :onnistui viesti/viestin-nayttoaika-lyhyt)
          (-> (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
              (dissoc :excel-virhe))))))

  HaePaikkauskohteet
  (process-event [_ app]
    (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app))

  HaePaikkauskohteetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [paikkauskohteet (map (fn [kohde]
                                 (-> kohde
                                     (assoc :formatoitu-aikataulu (fmt-aikataulu kohde))
                                     (assoc :formatoitu-sijainti
                                            (tr-domain/tr-osoite-moderni-fmt (:tie kohde) (:aosa kohde) (:aet kohde) (:losa kohde) (:let kohde)))
                                     (assoc :loppupvm-arvio (fmt-valmistuminen (:loppupvm kohde)))
                                     (assoc :paivays (or (:muokattu kohde) (:luotu kohde)))
                                     (assoc :toteumatyyppi (cond
                                                             (true? (:pot? kohde)) :pot
                                                             :else :normaali))))
                               vastaus)
          ;; Mikäli paikkauskohdelomake (avaimelle :lomake) on auki, pitää sen tiedot päivittää, koska oletettavasti on
          ;; tallennettu uusi toteuma kohteelle. Joten haetaan app-statesta samalla id:llä olevan paikkauskohteen tiedot lomakkeelle
          app (if (:toteuma-lisatty? app)
                (-> app
                    ;; Jos paikkauskohdelomake oli raportointitilassa ja muokkaustilassa laitetaan sama tila päälle
                    ;; :raportointila? true - appin juuressa
                    (assoc :lomake (some #(when (= (get-in app [:lomake :id]) (:id %))
                                            %) paikkauskohteet))
                    (assoc-in [:lomake :tyyppi] (:raportointi-tila? app))
                    (dissoc :toteuma-lisatty? :raportointi-tila?)) ;; Siivotaan lomakkeen tilaan liittyvät asiat pois
                (assoc app :lomake nil)) ;; Sulje mahdollinen lomake - jos ei lisätty toteumaa

          zoomattavat-geot (into [] (concat (mapv (fn [p]
                                                    (when (and
                                                            (not (nil? (:sijainti p)))
                                                            (not (empty? (:sijainti p))))
                                                      (harja.geo/extent (:sijainti p))))
                                                  paikkauskohteet)))]
      (do
        (if (and (not (nil? paikkauskohteet))
                 (not (empty? paikkauskohteet))
                 (not (nil? zoomattavat-geot))
                 (not (empty? zoomattavat-geot)))
          ;; Jos paikkauskohteita löytyy, resetoi kartta
          (do
            (reset! paikkauskohteet-kartalle/karttataso-paikkauskohteet paikkauskohteet)
            (reset! paikkauskohteet-kartalle/valitut-kohteet-atom (set (mapv :id paikkauskohteet))))
          ;; Jos paikkauskohteita ei löydy, poistetaan kaikki aiemmat paikkauskohteet kartalta
          (reset! paikkauskohteet-kartalle/karttataso-paikkauskohteet [])
          )
        (-> app
            (dissoc :haku-kaynnissa?)
            (assoc :pmr-lomake nil)
            (assoc :toteumalomake nil)
            (assoc :paikkauskohteet paikkauskohteet)))))

  HaePaikkauskohteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Paikkauskohteiden haku epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :haku-kaynnissa?)))

  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (let [lomake (laske-paikkauskohteen-pituus lomake [:lomake])
          _ (reset! lomake-atom lomake)
          {:keys [validoi] :as validoinnit} (validoi-lomake lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)]
      (-> app
          (assoc :lomake lomake)
          (assoc-in [:lomake ::tila/validius] validius)
          (assoc-in [:lomake ::tila/validi?] validi?))))

  TallennaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [;; Muutetaan paikkauskohteen tilaa vain, jos sitä ei ole asetettu
          paikkauskohde (if (nil? (:paikkauskohteen-tila paikkauskohde))
                          (assoc paikkauskohde :paikkauskohteen-tila "ehdotettu")
                          paikkauskohde)
          paikkauskohde (-> paikkauskohde
                            (siivoa-ennen-lahetysta)
                            (assoc :urakka-id (-> @tila/tila :yleiset :urakka :id)))]
      (do
        (tallenna-paikkauskohde paikkauskohde
                                ->TallennaPaikkauskohdeOnnistui
                                ->TallennaPaikkauskohdeEpaonnistui
                                [(not (nil? (:id paikkauskohde)))])
        app)))

  TallennaPaikkauskohdeOnnistui
  (process-event [{muokattu :muokattu paikkauskohde :paikkauskohde} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast!
        (if muokattu
          "Muutokset tallennettu"
          (str "Kohde " (paikkauskohde-id->nimi app (:id paikkauskohde)) " lisätty")))
      (dissoc app :lomake)))

  TallennaPaikkauskohdeEpaonnistui
  (process-event [{muokattu :muokattu paikkauskohde :paikkauskohde} app]
    (let [;; Otetaan virhe talteen
          virhe (get-in paikkauskohde [:response :virhe])
          virheteksti (get-in paikkauskohde [:parse-error :original-text])
          ulkoinen-id-virhe (when (and virhe (str/includes? virhe "ulkoinen-id"))
                              "Tarkista numero. Mahdollinen duplikaatti.")
          tierekisteri-virhe (when (and virheteksti (str/includes? virheteksti "Tierekisteriosoitteessa"))
                               "Tierekisteriosoitteessa virhe")]
      (do
        (if muokattu
          (viesti/nayta-toast! "Paikkauskohteen muokkaus epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton)
          (viesti/nayta-toast! "Paikkauskohteen tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton))
        (cond-> app
                true (assoc-in [:lomake :harja.tiedot.urakka.urakka/validi?] false)
                ulkoinen-id-virhe (update-in [:lomake :harja.tiedot.urakka.urakka/validius [:ulkoinen-id]]
                                             #(merge %
                                                     {:validi? false
                                                      :virheteksti ulkoinen-id-virhe}))
                tierekisteri-virhe (update-in [:lomake :harja.tiedot.urakka.urakka/validius [:tie]]
                                #(merge %
                                        {:validi? false
                                         :virheteksti tierekisteri-virhe}))))))

  TallennaPaikkauskohdeRaportointitilassa
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [merkitty-valmiiksi? (:paikkaustyo-valmis? paikkauskohde)
          valmistumispvm (:valiaika-valmistumispvm paikkauskohde)
          takuuaika (:valiaika-takuuaika paikkauskohde)
          tiemerkinta-tuhoutunut? (:tiemerkinta-tuhoutunut? paikkauskohde)
          paikkauskohde (-> paikkauskohde
                            ;; Paikkauskohden valmistumistilaa hallitaan checkboxilla, joten hanskataan tilanne, jossa paikkauskohde merkataan valmiiksi
                            (cond-> (and merkitty-valmiiksi? (= "tilattu" (:paikkauskohteen-tila paikkauskohde)))
                                    (assoc :paikkauskohteen-tila "valmis"))
                            ;; Paikkauskohde muutetaan valmiista tilatuksi, koska tapahtui jokin käyttäjävirhe - tilavaihdos
                            (cond-> (and (not merkitty-valmiiksi?) (= "valmis" (:paikkauskohteen-tila paikkauskohde)))
                                    (assoc :paikkauskohteen-tila "tilattu"))
                            ;; Paikkauskohde muutetaan valmiista tilatuksi, koska tapahtui jokin käyttäjävirhe - nollataan takuuaika
                            (cond-> (and (not merkitty-valmiiksi?) (= "valmis" (:paikkauskohteen-tila paikkauskohde)))
                                    (assoc :takuuaika nil))
                            (cond-> valmistumispvm (assoc :valmistumispvm valmistumispvm))
                            ;; Valmistumispäivämäärä pitää poistaa, jos valmis muutetaan tilatuksi
                            (cond-> (and (not merkitty-valmiiksi?) (= "valmis" (:paikkauskohteen-tila paikkauskohde))) (assoc :valmistumispvm nil))
                            (cond-> takuuaika (assoc :takuuaika takuuaika))
                            (cond-> tiemerkinta-tuhoutunut? (assoc :tiemerkinta-tuhoutunut? tiemerkinta-tuhoutunut?))
                            (siivoa-ennen-lahetysta))]
      (do
        (tallenna-paikkauskohde paikkauskohde
                                ->TallennaPaikkauskohdeOnnistui
                                ->TallennaPaikkauskohdeEpaonnistui
                                [(not (nil? (:id paikkauskohde)))])
        app)))

  TilaaPaikkauskohdeOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohde " (paikkauskohde-id->nimi app (:id vastaus)) " tilattu"))
      (dissoc app :lomake)))

  TilaaPaikkauskohdeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id vastaus)) " tilaamisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :lomake)))

  HylkaaPaikkauskohdeOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohde " (paikkauskohde-id->nimi app (:id vastaus)) " hylätty"))
      (dissoc app :lomake)))

  HylkaaPaikkauskohdeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id vastaus)) " hylkäämisessä tapahtui virhe!")
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :lomake)))

  PoistaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (do
      (tuck-apurit/post! :poista-paikkauskohde
                         (siivoa-ennen-lahetysta paikkauskohde)
                         {:onnistui ->PoistaPaikkauskohdeOnnistui
                          :epaonnistui ->PoistaPaikkauskohdeEpaonnistui
                          :paasta-virhe-lapi? true})
      app))

  PoistaPaikkauskohdeOnnistui
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohde " (:nimi paikkauskohde) " poistettu"))
      (dissoc app :lomake)))

  PoistaPaikkauskohdeEpaonnistui
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (:nimi paikkauskohde) " poistamisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :lomake)))

  PeruPaikkauskohteenTilausOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id vastaus)) " tilaus peruttu"))
      (dissoc app :lomake)))

  PeruPaikkauskohteenTilausEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id vastaus)) " tilauksen perumisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :lomake)))

  PeruPaikkauskohteenHylkaysOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id vastaus)) " hylkäys peruttu"))
      (dissoc app :lomake)))

  PeruPaikkauskohteenHylkaysEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)
          _ (modal/piilota!)]
      (viesti/nayta-toast! (str "Kohteen " (paikkauskohde-id->nimi app (:id vastaus)) " hylkäyksen perumisessa tapahtui virhe!")
                           :varoitus viesti/viestin-nayttoaika-aareton)
      (dissoc app :lomake)))

  LaskePituusOnnistui
  (process-event [{vastaus :vastaus lomakeavain :lomakeavain} app]
    (let [app (assoc-in app [lomakeavain :pituus] (:pituus vastaus))
          pituuskentat (merge @lomakkeen-pituuskentat
                              {:pituus (:pituus vastaus)})
          _ (reset! lomakkeen-pituuskentat pituuskentat)]
      app))

  LaskePituusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;; Kohteen pituutta ei voitu laskea
    (do
      (js/console.log "Pituutta ei voitu laskea: vastaus" (pr-str vastaus))
      app))

  JarjestaPaikkauskohteet
  (process-event [{jarjestys :jarjestys} app]
    (let [vanha-jarjestys (get-in app [:jarjestys :nimi])
          kaanteinen? (if (= jarjestys vanha-jarjestys)
                        (not (get-in app [:jarjestys :kaanteinen?]))
                        false)]
      (-> app
          (assoc-in [:jarjestys :nimi] jarjestys)
          (assoc-in [:jarjestys :kaanteinen?] kaanteinen?)
          (assoc :paikkauskohteet (sort-by jarjestys (if kaanteinen? kaanteinen-jarjestaja compare) (:paikkauskohteet app))))))

  AsetaToteumatyyppi
  (process-event [{uusi-tyyppi :uusi-tyyppi} app]
    (assoc-in app [:lomake :toteumatyyppi] uusi-tyyppi))
)

(defn kayttaja-on-urakoitsija? [urakkaroolit]
  (let [urakkaroolit (if (set? urakkaroolit)
                       urakkaroolit
                       #{urakkaroolit})
        urakoitsijaroolit #{"Laatupaallikko"
                            "Kayttaja"
                            "vastuuhenkilo"
                            "Laadunvalvoja"
                            "Kelikeskus"
                            "Paivystaja"}]
    ;; Annetut roolit set voi olla kokonaan tyhjä
    (cond
      ;; Jos urakoitsija, niin urakoitsija
      (= :urakoitsija (roolit/osapuoli @istunto/kayttaja))
      true

      ;; Jos tyhjä, ei ole urakoitsija
      (empty? urakkaroolit)
      false

      ;; Jos rooli on annettu, tarkista onko urakoitsija
      (some (fn [rooli]
              (true?
                (some #(= rooli %) urakoitsijaroolit)))
            urakkaroolit)
      true
      :else false)))

(defn nayta-modal [otsikko viesti ok-nappi peruuta-nappi]
  (fn [] (modal/nayta!
           {:modal-luokka "harja-modal-keskitetty"
            :luokka "modal-dialog-keskitetty"}
           [:div
            {:style
             {:display :flex
              :flex-direction :column
              :align-items :center}}
            [:div
             {:style
              {:margin-top "3rem"
               :font-size "16px"
               :font-weight "bold"}}
             otsikko]
            [:div
             {:style
              {:margin-top "1rem"}}
             viesti]
            [:div
             {:style
              {:margin-top "3rem"
               :margin-bottom "3rem"
               :display :flex
               :width "100%"
               :justify-content "center"}}
             ok-nappi
             peruuta-nappi]])))

(defn urapaikkauksen-sijainti-fmt
  "Formatoi urapaikkausten sijainnit. Input voi olla joko numeerinen arvo esim. 1, tai sitten vectorissa arvot [1 2].
  Tehdään yksi formatointifunktio, joka osaa näyttää näissä kaikissa tilanteissa luvut lukutilassa oikein.
  Ja lisäksi näytetään viiva - mikäli arvoa ei ole annettu ollenkaan."
  [v]
  (cond
    (vector? v)
    (clojure.string/join ", " v)

    (or (nil? v) (and (seq? v) (empty? v)))
    "-"

    :else
    v))
