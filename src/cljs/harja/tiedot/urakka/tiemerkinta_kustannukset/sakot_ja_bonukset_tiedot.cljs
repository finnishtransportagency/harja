(ns harja.tiedot.urakka.tiemerkinta-kustannukset.sakot-ja-bonukset-tiedot
  "Tiemerkintöjen sakot ja bonukset - tiedot"
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom] :as reagent]

            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.ui.lomake :as lomake]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.raportit :as raporttitiedot]
            [harja.views.urakka.tiemerkinta-kustannukset.yhteiset :as yhteiset]))

(defonce ^{:private true} raportti-avain :tiemerkinta-sakot-bonukset)
(defonce ^{:private true} nollatut-valinnat {:rivit nil
                                             :kohteet {}
                                             :liitteet {}
                                             :valittu-rivi {}
                                             :muokataan false
                                             :ladatut-rivit nil
                                             :haku-kaynnissa? true
                                             :valinnat {:raportti {}}})

(defonce nakymassa? (atom false))
(defonce ei-kohdetta-teksti "Ei liity kohteeseen")


(defn voi-tallentaa?
  [{:keys [summa toimenpideinstanssi lomake-selite laji kasittelyaika] :as _valittu-rivi}]
  (let [kustannus-olemassa? (some? summa)
        pvm-validi? (pvm/pvm? kasittelyaika)
        selite-olemassa? (and
                           (some? lomake-selite)
                           (> (count lomake-selite) 0))
        laji-validi? (contains? (set (keys yhteiset/laji-valinnat)) laji)
        toimenpideinstanssi-olemassa? (some? toimenpideinstanssi)]
    (and
      pvm-validi?
      laji-validi?
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
(defrecord TallennaRivi [rivi virheita? liitteet])
(defrecord TallennusOnnistui [vastaus])
(defrecord TallennusEpaonnistui [vastaus])
(defrecord UusiLiite [liite])
(defrecord AsetaToteumanPvm [aika])
(defrecord UusiSanktio [tyyppi])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))


(defn- raporttiparametrit [rivit laji]
  (raporttitiedot/urakkaraportin-parametrit @nav/valittu-urakka-id raportti-avain
    {:laji laji
     :rivit rivit
     :alkupvm  (-> @u/valittu-aikavali first)
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


(defn- liita-liitteet-toteumille
  "Lisää liitteen jokaiselle toteumalle
   Toteuman :id arvo vastaa liitteen :sanktio_id arvoa"
  [app vastaus]
  (update app :ladatut-rivit (fn [rivit]
                               (mapv (fn [rivi]
                                       (let [sanktion-liitteet (into [] (filter #(= (:sanktio_id %) (:id rivi)) vastaus))]
                                         (assoc rivi :liitteet sanktion-liitteet)))
                                 rivit))))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (->
      (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true)
      (assoc-in [:valinnat :aikavali] @u/valittu-aikavali)))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (hae-liitteet (-> app
                    ;; Miksi :ladatut-rivit rivit -
                    ;; Halutaan pitää :rivit vielä tyhjänä, jotta ajax-loader näkyy
                    ;; Ja näytetään rivit vasta kun kaikki tiedot on ladattu sekä liitteet liitetty
                    (assoc :ladatut-rivit vastaus)
                    (assoc-in [:valinnat :raportti] (raporttiparametrit vastaus (get-in app [:valinnat :valittu-laji]))))))

  HaeTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeLiitteetOnnistui
  (process-event [{:keys [vastaus]} app]
    (hae-kohteet (liita-liitteet-toteumille app vastaus)))

  HaeLiitteetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeKohteetOnnistui
  (process-event [{:keys [vastaus]} {:keys [ladatut-rivit] :as app}]
    (-> app
      (assoc :kohteet vastaus)
      (assoc :rivit ladatut-rivit)
      (assoc :haku-kaynnissa? false)))

  HaeKohteetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  UusiLiite
  (process-event [{:keys [liite]} app]
    (assoc-in app [:valittu-rivi :uudet-liitteet] [liite]))

  UusiSanktio
  (process-event [{:keys [tyyppi]} app]
    (let [nyt (pvm/nyt)
          uusi-sanktio {:laji tyyppi
                        :suorasanktio true
                        :toimenpideinstanssi (:tpi_id (first @u/urakan-toimenpideinstanssit))
                        :laatupoikkeama {:aika nyt
                                         :tekijanimi @istunto/kayttajan-nimi
                                         :paatos {:paatos "sanktio"
                                                  :kasittelytapa :muu
                                                  :muukasittelytapa "Tiemerkintä"
                                                  :kasittelyaika (get-in app [:valittu-rivi :kasittelyaika])}}}

          uusi-bonus {:laji nil
                      :kasittelyaika (get-in app [:valittu-rivi :kasittelyaika])
                      :toimenpideinstanssi (when (= 1 (count @u/urakan-toimenpideinstanssit))
                                             (:tpi_id (first @u/urakan-toimenpideinstanssit)))
                      :laatupoikkeama {:aika nyt
                                       :tekijanimi @istunto/kayttajan-nimi}}]
      (cond
        (= tyyppi :yllapidon_bonus)
        (update-in app [:valittu-rivi] merge uusi-bonus)

        (= tyyppi :yllapidon_sakko)
        (update-in app [:valittu-rivi] merge uusi-sanktio))))

  TallennaRivi
  (process-event [{:keys [rivi virheita? liitteet]} {:keys [valittu-rivi] :as app}]
    ;; Tämä enabloi lomakkeen validoinnin
    ;; Saavutettavuusmielessä, näytetään validointi tallennus painalluksen yhteydessä
    (if (or
          virheita?
          (not (voi-tallentaa? valittu-rivi)))
      ;; Jos ei voida tallentaa, kerro käyttäjälle missä virheet
      (assoc-in app [:valittu-rivi :virheita?] true)

      ;; Voidaan tehdä tallennus 
      (let [rivi (lomake/ilman-lomaketietoja rivi)
            {:keys [id summa indeksi toimenpideinstanssi
                    kasittelyaika lomake-selite laatupoikkeama laji]} rivi

            uudet-liitteet (:uudet-liitteet valittu-rivi)
            yllapitokohde-id (-> rivi :yllapitokohde :id)
            laatupoikkeama (-> laatupoikkeama
                             (assoc-in [:paatos :perustelu] lomake-selite)
                             (assoc-in [:paatos :kasittelyaika] kasittelyaika))

            sanktio (-> rivi
                      (assoc :perintapvm kasittelyaika)
                      (dissoc :laatupoikkeama :yllapitokohde))

            parametrit (cond
                         ;; Sakot 
                         (= laji :yllapidon_sakko)
                         {:sanktio        sanktio
                          :laatupoikkeama (-> laatupoikkeama
                                            (assoc :urakka @nav/valittu-urakka-id)
                                            (assoc :yllapitokohde yllapitokohde-id)
                                            (assoc :uusi-liite uudet-liitteet)
                                            (assoc-in [:paatos :paatos] "sanktio")
                                            (assoc-in [:paatos :kasittelytapa] :muu)
                                            (assoc-in [:paatos :muukasittelytapa] "Tiemerkintä")
                                            (assoc-in [:paatos :kasittelyaika] (get-in app [:valittu-rivi :kasittelyaika])))
                          :hoitokausi @u/valittu-hoitokausi}

                         ;; Bonukset
                         (= laji :yllapidon_bonus)
                         {:sanktio
                          {:id id
                           :laji :yllapidon_bonus
                           :suorasanktio true
                           :summa summa
                           :indeksi indeksi
                           :perintapvm kasittelyaika
                           :liitteet liitteet
                           :toimenpideinstanssi toimenpideinstanssi}
                          :laatupoikkeama {:id (:id laatupoikkeama)
                                           :tekijanimi @istunto/kayttajan-nimi
                                           :urakka @nav/valittu-urakka-id
                                           :yllapitokohde yllapitokohde-id
                                           :aika kasittelyaika
                                           :uusi-liite uudet-liitteet
                                           :paatos {:paatos "sanktio"
                                                    :perustelu lomake-selite
                                                    :kasittelyaika kasittelyaika
                                                    :kasittelytapa :muu
                                                    :muukasittelytapa "Tiemerkintä"}}
                          :hoitokausi @u/valittu-hoitokausi})]

        (tuck-apurit/post! app :tallenna-suorasanktio
          parametrit
          {:onnistui ->TallennusOnnistui
           :epaonnistui ->TallennusEpaonnistui})

        (-> app
          (assoc :rivit nil)
          (assoc :muokataan false)
          (assoc :haku-kaynnissa? true)
          (assoc-in [:valittu-rivi :virheita?] false)))))

  TallennusOnnistui
  (process-event [_ {:keys [valittu-rivi rivit] :as app}]
    (let [valittu-id (:id valittu-rivi)
          valittu-rivi (some #(when (= (:id %) valittu-id) %) rivit)]
      (hae-tiedot app)
      (viesti/nayta-toast! "Toteuma tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
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
