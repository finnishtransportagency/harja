(ns harja.views.urakka.kustannusten-kirjaus.sakot-bonukset-tiedot
  "Tiemerkintöjen sakot ja bonukset - tiedot"
  (:require [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.ui.lomake :as lomake]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tyokalut.tuck :as tuck-apurit]
            [reagent.core :refer [atom] :as reagent]
            [harja.tiedot.raportit :as raporttitiedot]))

(defonce ^{:private true} raportti-avain :tiemerkinta-sakot-bonukset)
(defonce ^{:private true} nollatut-valinnat {:rivit nil
                                             :kohteet {}
                                             :liitteet {}
                                             :muokataan false
                                             :haku-kaynnissa? false
                                             :valittu-rivi {:uusi-liite [{}]}
                                             :valinnat {:raportti {}
                                                        :uusi-liite {}
                                                        :lajit {:yllapidon_sakko "Sakko"
                                                                :yllapidon_bonus "Bonus"}}})

(defonce nakymassa? (atom false))
(defonce tila (atom (assoc-in nollatut-valinnat [:valinnat :valittu-laji] :kaikki)))

(defonce laji-valinnat {:kaikki "Kaikki"
                        :yllapidon_sakko "Sakko"
                        :yllapidon_bonus "Bonus"})


(defn- nollaa-tuck-tila
  "Nollaa Tuck-tilan osittain säilyttäen olemassa olevat syvemmän tason arvot.
   Korvaa arvot, jotka on määritelty `nollatut-valinnat`
   Käytetään kun suodattimia päivitetään, urakkaa vaihdetaan, yms, jotta tilaan ei jää mitään roikkumaan."
  [app]
  (merge-with (fn [app valinta]
                (if (and
                      (map? app) (map? valinta))
                  (merge app valinta)
                  valinta))
    app
    nollatut-valinnat))


(defn voi-tallentaa?
  "Validoi toteuman muokkauslomakkeen"
  [{:keys [summa toimenpideinstanssi lomake-selite yllapitokohde laji kasittelyaika] :as valittu-rivi} kohteet]
  (let [kustannus-olemassa? (some? summa)
        pvm-validi? (pvm/pvm? kasittelyaika)
        selite-olemassa? (some? lomake-selite)
        laji-validi? (contains? (set (keys laji-valinnat)) laji)
        toimenpideinstanssi-olemassa? (some? toimenpideinstanssi)
        kohde-olemassa? (boolean (some #(= (:id yllapitokohde) (:id %)) kohteet))]
    (and
      pvm-validi?
      laji-validi?
      kohde-olemassa?
      selite-olemassa?
      kustannus-olemassa?
      toimenpideinstanssi-olemassa?)))


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord HaeLiitteetOnnistui [vastaus])
(defrecord HaeLiitteetEpaonnistui [vastaus])
(defrecord HaeKohteetOnnistui [vastaus])
(defrecord HaeKohteetEpaonnistui [vastaus])
(defrecord AvaaModal [rivi])
(defrecord MuokkaaRivia [rivi])
(defrecord SuljeMuokkaus [])
(defrecord ValitseLaji [rivi])
(defrecord TallennaRivi [rivi])
(defrecord TallennusOnnistui [vastaus])
(defrecord TallennusEpaonnistui [vastaus])
(defrecord UusiLiite [liite])
(defrecord AsetaToteumanPvm [aika])
(defrecord UusiSanktio [tyyppi])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))


(defn- raporttiparametrit []
  (raporttitiedot/urakkaraportin-parametrit @nav/valittu-urakka-id raportti-avain
    {:alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)
     :urakkatyyppi (:arvo @nav/urakkatyyppi)}))


(defn hae-tiedot
  [{:keys [valinnat] :as app}]
  (let [{:keys [valittu-laji]} valinnat]
    (tuck-apurit/post! app :hae-urakan-sanktiot-ja-bonukset
      {:urakka-id @nav/valittu-urakka-id
       :alku      (-> @u/valittu-aikavali first)
       :loppu     (-> @u/valittu-aikavali second)
       :hae-sanktiot? (or (= valittu-laji :kaikki) (= valittu-laji :yllapidon_sakko))
       :hae-bonukset? (or (= valittu-laji :kaikki) (= valittu-laji :yllapidon_bonus))}
      {:onnistui ->HaeTiedotOnnistui
       :epaonnistui ->HaeTiedotEpaonnistui})))


(defn hae-liitteet [app]
  (tuck-apurit/post! app :hae-urakan-liitteet
    {:urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeLiitteetOnnistui
     :epaonnistui ->HaeLiitteetEpaonnistui}))


(defn hae-kohteet [app]
  (tuck-apurit/post! app :urakan-yllapitokohteet-lomakkeelle
    {:urakka-id @nav/valittu-urakka-id :sopimus-id (-> @u/valittu-sopimusnumero :sopimus-id)}
    {:onnistui ->HaeKohteetOnnistui
     :epaonnistui ->HaeKohteetEpaonnistui}))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (-> 
      (nollaa-tuck-tila app)
      (assoc :haku-kaynnissa? true)
      (assoc-in [:valinnat :aikavali] @u/valittu-aikavali)))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (hae-liitteet (-> app
                    (assoc :rivit vastaus :haku-kaynnissa? false)
                    (assoc-in [:valinnat :raportti] (raporttiparametrit)))))

  HaeTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeLiitteetOnnistui
  (process-event [{:keys [vastaus]} app]
    (hae-kohteet (assoc app :liitteet vastaus)))

  HaeLiitteetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeKohteetOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :kohteet vastaus))

  HaeKohteetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  UusiLiite
  (process-event [{:keys [liite]} app]
    (assoc-in app [:valittu-rivi :laatupoikkeama :uusi-liite] liite))

  UusiSanktio
  (process-event [{:keys [tyyppi]} app]
    (let [nyt (pvm/nyt)
          default-perintapvm (pvm/luo-pvm-dec-kk (pvm/vuosi nyt) (pvm/kuukausi nyt) 15)
          uusi-sanktio {:laji tyyppi
                        :suorasanktio true
                        :perintapvm default-perintapvm
                        :toimenpideinstanssi (:tpi_id (first @u/urakan-toimenpideinstanssit))
                        :laatupoikkeama {:tekijanimi @istunto/kayttajan-nimi
                                         :aika nyt
                                         :paatos {:paatos "sanktio"
                                                  :kasittelyaika (get-in app [:valittu-rivi :kasittelyaika])
                                                  :kasittelytapa :muu
                                                  :muukasittelytapa "Tiemerkintä"}}}

          uusi-bonus {:laji nil
                      :aika nyt
                      :perintapvm default-perintapvm
                      :kasittelyaika (get-in app [:valittu-rivi :kasittelyaika])
                      :toimenpideinstanssi (when (= 1 (count @u/urakan-toimenpideinstanssit))
                                             (:tpi_id (first @u/urakan-toimenpideinstanssit)))
                      :laatupoikkeama {:tekijanimi @istunto/kayttajan-nimi
                                       :aika nyt}}]

      (cond
        (= tyyppi :yllapidon_bonus)
        (update-in app [:valittu-rivi] merge uusi-bonus)

        (= tyyppi :yllapidon_sakko)
        (update-in app [:valittu-rivi] merge uusi-sanktio))))

  TallennaRivi
  (process-event [{:keys [rivi]} app]
    (let [rivi (lomake/ilman-lomaketietoja rivi)
          yllapitokohde-id (-> rivi :yllapitokohde :id)

          {:keys [laji id summa indeksi perintapvm toimenpideinstanssi kasittelyaika kasittelytapa lomake-selite laatupoikkeama]} rivi
          laatupoikkeama (assoc-in laatupoikkeama [:paatos :perustelu] lomake-selite)

          parametrit (cond
                       ;; Sakot 
                       (= laji :yllapidon_sakko)
                       {:sanktio        (dissoc rivi :laatupoikkeama :yllapitokohde)
                        :laatupoikkeama (assoc
                                          laatupoikkeama
                                          :urakka @nav/valittu-urakka-id
                                          :yllapitokohde yllapitokohde-id)
                        :hoitokausi @u/valittu-hoitokausi}

                       ;; Bonukset
                       (= laji :yllapidon_bonus)
                       {:sanktio
                        {:id id
                         :laji :yllapidon_bonus
                         :suorasanktio true
                         :summa summa
                         :indeksi indeksi
                         :perintapvm perintapvm
                         :toimenpideinstanssi toimenpideinstanssi}
                        :laatupoikkeama {:tekijanimi @istunto/kayttajan-nimi
                                         :urakka @nav/valittu-urakka-id
                                         :yllapitokohde yllapitokohde-id
                                         :aika kasittelyaika
                                         :paatos {:paatos "sanktio"
                                                  :perustelu lomake-selite
                                                  :kasittelyaika kasittelyaika
                                                  :kasittelytapa :muu
                                                  :muukasittelytapa "Tiemerkintä"}}
                        :hoitokausi @u/valittu-hoitokausi})]

      ;; ->>
      (tuck-apurit/post! app :tallenna-suorasanktio
        parametrit
        {:onnistui ->TallennusOnnistui
         :epaonnistui ->TallennusEpaonnistui})
      (assoc app :muokataan false)))

  TallennusOnnistui
  (process-event [_ {:keys [valittu-rivi rivit] :as app}]
    (let [valittu-id (:id valittu-rivi)
          valittu-rivi (some #(when (= (:id %) valittu-id) %) rivit)]
      (viesti/nayta-toast! "Toteuma tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
      (hae-tiedot app)
      (assoc app :valitu-rivi valittu-rivi)))

  TallennusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (epaonnistui vastaus app))

  MuokkaaRivia
  (process-event [{:keys [rivi]} app]
    (update app :valittu-rivi merge rivi))

  AsetaToteumanPvm
  (process-event [{aika :aika} app]
    (assoc-in app [:valittu-rivi :kasittelyaika] aika))

  AvaaModal
  (process-event [{:keys [rivi]} app]
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi)
      (assoc-in [:valittu-rivi :lomake-selite]
        (or (-> rivi :lisatieto) (-> rivi :laatupoikkeama :paatos :perustelu)))))

  SuljeMuokkaus
  (process-event [_ app]
    (assoc app :muokataan false))

  ValitseLaji
  (process-event [{:keys [rivi]} app]
    (assoc-in app [:valinnat :valittu-laji] rivi)))
