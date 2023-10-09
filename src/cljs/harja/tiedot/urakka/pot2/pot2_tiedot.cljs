(ns harja.tiedot.urakka.pot2.pot2-tiedot
  (:require
    [reagent.core :refer [atom] :as r]
    [tuck.core :refer [process-event] :as tuck]
    [clojure.string :as str]
    [harja.fmt :as fmt]
    [harja.domain.pot2 :as pot2-domain]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.loki :refer [log tarkkaile!]]
    [harja.ui.lomakkeen-muokkaus :as lomakkeen-muokkaus]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.domain.oikeudet :as oikeudet]
    [harja.ui.grid.gridin-muokkaus :as gridin-muokkaus]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.ui.viesti :as viesti]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defonce pot2-nakymassa? (atom false))
(defonce kohdeosat-atom (atom nil))
(defonce kohdeosat-varoitukset-atom (atom {}))
(defonce kohdeosat-virheet-atom (atom {}))
(defonce alustarivit-atom (atom nil))
(defonce alustarivit-varoitukset-atom (atom {}))
(defonce alustarivit-virheet-atom (atom {}))
(defonce lisatiedot-atom (atom nil))

(defrecord MuutaTila [polku arvo])
(defrecord PaivitaTila [polku f])
(defrecord HaePot2Tiedot [paallystyskohde-id paikkauskohde-id])
(defrecord HaePot2TiedotOnnistui [vastaus])
(defrecord HaePot2TiedotEpaonnistui [vastaus])
(defrecord AsetaTallennusKaynnissa [])
(defrecord TallennaPot2Tiedot [valmis-kasiteltavaksi?])
(defrecord KopioiToimenpiteetTaulukossaKaistoille [rivi toimenpiteet-taulukko-atom sort-atom])
(defrecord KopioiToimenpiteetTaulukossaAjoradoille [rivi toimenpiteet-taulukko-atom sort-atom])
(defrecord AvaaAlustalomake [lomake])
(defrecord PaivitaAlustalomake [alustalomake])
(defrecord TallennaAlustalomake [alustalomake jatka?])
(defrecord SuljeAlustalomake [])
(defrecord NaytaMateriaalilomake [rivi sivulle?])
(defrecord SuljeMateriaalilomake [])
(defrecord Pot2Muokattu [])
(defrecord LisaaPaallysterivi [atomi])
(defrecord KulutuskerrosMuokattu [muokattu?])
(defrecord LaskeTieosoitteenPituus [tie])
(defrecord LaskeTieosoitteenPituusOnnistui [vastaus])
(defrecord LaskeTieosoitteenPituusVirhe [vastaus])

(defn- lisaa-uusi-paallystekerrosrivi!
  [rivit-atom perustiedot]
  (reset! rivit-atom (yllapitokohteet/lisaa-paallystekohdeosa @rivit-atom (count @rivit-atom) (:tr-osoite perustiedot))))

(defn tayta-alas?-fn
  [arvo]
  (some? arvo))

(defn onko-alustatoimenpide-verkko? [koodi]
  (= koodi 3))

(defn toimenpiteen-tiedot
  [{:keys [koodistot]} rivi]
  (fn [{:keys [koodistot]} rivi]
    [:div.pot2-toimenpiteen-tiedot
     ;; Kaivetaan metatiedosta sopivat kentät. Tähän mahdollisimman geneerinen ratkaisu olisi hyvä
     (when (some? rivi)
       (str/capitalize
         (let [kuuluu-kentalle? (fn [{:keys [nimi]}]
                                  (and nimi
                                       (not= nimi :massa)
                                       (not= nimi :murske)
                                       (not= nimi :verkon-tyyppi)))
               muotoile-kentta (fn [{:keys [otsikko yksikko nimi valinnat-koodisto valinta-arvo valinta-nayta] :as metadata}]
                                 (let [kentan-arvo (nimi rivi)
                                       teksti (if valinnat-koodisto
                                                (let [koodisto (valinnat-koodisto koodistot)
                                                      koodisto-rivi (some #(when (= (valinta-arvo %) kentan-arvo) %) koodisto)
                                                      koodisto-teksti (valinta-nayta koodisto-rivi)]
                                                  koodisto-teksti)
                                                (str kentan-arvo
                                                     (when (some? yksikko)
                                                       (str " " yksikko))))
                                       otsikko? (not (contains? #{:verkon-sijainti :verkon-tarkoitus
                                                                  :sideaine :sideainepitoisuus :sideaine2
                                                                  :massamaara :kokonaismassamaara} nimi))]

                                   (str (when otsikko? (str otsikko ": ")) teksti)))]
           (str/join "; " (->> (pot2-domain/alusta-toimenpidespesifit-metadata rivi)
                               (filter kuuluu-kentalle?)
                               (map muotoile-kentta))))))]))

(defn merkitse-muokattu [app]
  (assoc-in app [:paallystysilmoitus-lomakedata :muokattu?] true))

(defn rivi->massa-tai-murske
  "Kaivaa POT2 kulutuskerroksen, alustarivin, massarivin tai murskerivin pohjalta ko. massan tai murskeen kaikki tiedot"
  [rivi {:keys [massat murskeet]}]
  (let [murske-id (or (::pot2-domain/murske-id rivi) (:murske rivi))
        massa-id (or (::pot2-domain/massa-id rivi) (:massa rivi))]
    (if murske-id
      (first (filter #(when (= (::pot2-domain/murske-id %) murske-id)
                        %)
                     murskeet))
      (first (filter #(when (= (::pot2-domain/massa-id %) massa-id)
                        %)
                     massat)))))

(defn jarjesta-ja-indeksoi-atomin-rivit
  [rivit-atom sort-fn]
  (reset! rivit-atom
          (yllapitokohteet-domain/indeksoi-kohdeosat
            (sort-by sort-fn (vals @rivit-atom)))))

(defn- jarjesta-rivit-fn-mukaan [sort-fn rivit]
  (yllapitokohteet-domain/indeksoi-kohdeosat
    (sort-by sort-fn rivit)))

(defn toimenpiteen-teksti
  [rivi materiaalikoodistot]
  (if (onko-alustatoimenpide-verkko? (:toimenpide rivi))
    (pot2-domain/ainetyypin-koodi->nimi (:verkon-tyypit materiaalikoodistot) (:verkon-tyyppi rivi))
    (pot2-domain/ainetyypin-koodi->lyhenne (:alusta-toimenpiteet materiaalikoodistot) (:toimenpide rivi))))

(defn- materiaali
  [massat-tai-murskeet {:keys [massa-id murske-id]}]
  (first (filter #(or (and (some? massa-id)
                           (= (::pot2-domain/massa-id %) massa-id))
                      (and (some? murske-id)
                           (= (::pot2-domain/murske-id %) murske-id)))
                 massat-tai-murskeet)))

(defn tunnista-materiaali
  "Tunnistaa rivin, massojen ja murskeiden avulla, mikä massa tai murske on kyseessä."
  [rivi massat murskeet]
  (let [massa-id (or (:massa rivi) (:massa-id rivi))
        murske-id (:murske rivi)]
    (cond
      massa-id
      (materiaali massat {:massa-id massa-id})

      murske-id
      (materiaali murskeet {:murske-id murske-id})

      :else
      nil)))

(def +nil-materiaalin-sort-str+ "zzz") ;; alustarivit joista materiaali puuttuu, menevät taulukon hännille

(defn materiaalin-sort-fn [rivi massat murskeet materiaalikoodistot]
  (assert (and rivi massat murskeet materiaalikoodistot) "Tällä kertaa kaikki funktion parametrit ovat pakollisia")
  (if-let [materiaali (tunnista-materiaali rivi massat murskeet)]
    (mk-tiedot/materiaalin-rikastettu-nimi {:tyypit ((if (::pot2-domain/murske-id materiaali)
                                                       :mursketyypit
                                                       :massatyypit) materiaalikoodistot)
                                            :materiaali materiaali})
    +nil-materiaalin-sort-str+))

(def valittu-alustan-sort (atom :tieosoite))
(def valittu-paallystekerros-sort (atom :tieosoite))

(defn jarjesta-valitulla-sort-funktiolla
  "Riippuen siitä mikä sort avain on valittu, palautetaan oikea funktio"
  [valittu-sort {:keys [massat murskeet materiaalikoodistot]} rivi]
  (case valittu-sort
    :tieosoite
    (yllapitokohteet-domain/yllapitokohteen-jarjestys rivi)

    :toimenpide
    ;; sorttaus menee pieleen, jos TJYR isolla mutta Teräsverkko pienellä
    ;; lower-case nillille aiheuttaa NPE:n, ehkäistään se when-letillä
    (when-let [toimenpide (toimenpiteen-teksti rivi materiaalikoodistot)]
      (str/lower-case toimenpide))

    :materiaali
    (materiaalin-sort-fn rivi massat murskeet materiaalikoodistot)

    :kaista
    (yllapitokohteet-domain/yllapitokohteen-jarjestys rivi true)

    :else
    (yllapitokohteet-domain/yllapitokohteen-jarjestys rivi)))

(defn pot2-haun-vastaus->lomakedata
  "Muuntaa palvelimelta saadut pot2-tiedot käyttöliittymän ymmärtämään rakenteeseen"
  [vastaus urakka-id]
  (let [vastaus (assoc vastaus :versio 2)
        perustiedot (select-keys vastaus paallystys/perustiedot-avaimet)
        lahetyksen-tila (select-keys vastaus paallystys/lahetyksen-tila-avaimet)
        kulutuskerros (:paallystekerros vastaus)
        alusta (:alusta vastaus)]
    {:paallystyskohde-id (:paallystyskohde-id vastaus)
     :perustiedot (merge perustiedot
                         {:tr-osoite (select-keys perustiedot paallystys/tr-osoite-avaimet)
                          :takuupvm (or (:takuupvm perustiedot) paallystys/oletus-takuupvm)})
     :lahetyksen-tila lahetyksen-tila
     :kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystysilmoitukset urakka-id)
     :paallystekerros kulutuskerros
     :alusta alusta
     :lisatiedot (:lisatiedot vastaus)
     :kulutuskerros-muokattu? false}))

(extend-protocol tuck/Event

  MuutaTila
  (process-event [{:keys [polku arvo]} app]
    (assoc-in app polku arvo))
  PaivitaTila
  (process-event [{:keys [polku f]} app]
    (update-in app polku f))

  HaePot2Tiedot
  (process-event [{paallystyskohde-id :paallystyskohde-id paikkauskohde-id :paikkauskohde-id} {urakka :urakka :as app}]
    (let [parametrit {:urakka-id (:id urakka)
                      :paallystyskohde-id paallystyskohde-id
                      :paikkauskohde? (if paikkauskohde-id true false)}]
      (tuck-apurit/post! app
        :urakan-paallystysilmoitus-paallystyskohteella
        parametrit
        {:onnistui ->HaePot2TiedotOnnistui
         :epaonnistui ->HaePot2TiedotEpaonnistui})))

  HaePot2TiedotOnnistui
  (process-event [{vastaus :vastaus} {urakka :urakka :as app}]
    (let [lomakedata (pot2-haun-vastaus->lomakedata vastaus (:id urakka))]
      (-> app
        (assoc :paallystysilmoitus-lomakedata lomakedata))))

  HaePot2TiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! (str "Tietojen haku epäonnistui: " vastaus) :danger viesti/viestin-nayttoaika-pitka)
    app)

  AsetaTallennusKaynnissa
  (process-event [_ app]
    (assoc-in app [:paallystysilmoitus-lomakedata :tallennus-kaynnissa?] true))

  TallennaPot2Tiedot
  (process-event [{:keys [valmis-kasiteltavaksi?]} 
                  {{urakka-id :id :as urakka} :urakka
                   {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila
                   paallystysilmoitus-lomakedata :paallystysilmoitus-lomakedata :as app}]
    (let [lahetettava-data (-> paallystysilmoitus-lomakedata
                             (select-keys #{:perustiedot :ilmoitustiedot :paallystyskohde-id})
                             (update :perustiedot lomakkeen-muokkaus/ilman-lomaketietoja)
                             (update-in [:perustiedot :asiatarkastus] lomakkeen-muokkaus/ilman-lomaketietoja)
                             (update-in [:perustiedot :tekninen-osa] lomakkeen-muokkaus/ilman-lomaketietoja)
                             (assoc-in [:perustiedot :valmis-kasiteltavaksi] valmis-kasiteltavaksi?)
                             (assoc :lisatiedot @lisatiedot-atom
                               :versio 2)
                             (update :ilmoitustiedot dissoc :virheet)
                             (assoc :paallystekerros (gridin-muokkaus/filteroi-uudet-poistetut
                                                       (into (sorted-map)
                                                         @kohdeosat-atom)))
                             (assoc :alusta (gridin-muokkaus/filteroi-uudet-poistetut
                                              (into (sorted-map)
                                                @alustarivit-atom))))
          ;; Mikäli lomakkeella pyritään täydentämään paikkauskohteen pot ilmoitusta, niin siirrä data oman avaimen alle
          lahetettava-data (if-not (:paikkauskohteet? app)
                             lahetettava-data
                             (assoc lahetettava-data
                               :paikkauskohteen-tiedot
                               {:aloituspvm (get-in paallystysilmoitus-lomakedata [:perustiedot :aloituspvm])
                                :paallystys-alku (get-in paallystysilmoitus-lomakedata [:perustiedot :paallystys-alku])
                                :valmispvm-paallystys (get-in paallystysilmoitus-lomakedata [:perustiedot :valmispvm-paallystys])
                                :valmispvm-kohde (get-in paallystysilmoitus-lomakedata [:perustiedot :valmispvm-kohde])
                                :takuuaika (get-in paallystysilmoitus-lomakedata [:perustiedot :takuuaika])}))]
      (log "TallennaPot2Tiedot lahetettava-data: " (pr-str lahetettava-data))
      (tuck-apurit/post! app :tallenna-paallystysilmoitus
        {:urakka-id urakka-id
         :sopimus-id (first valittu-sopimusnumero)
         :vuosi valittu-urakan-vuosi
         :paallystysilmoitus lahetettava-data}
        {:onnistui paallystys/->TallennaPaallystysilmoitusOnnistui
         :epaonnistui paallystys/->TallennaPaallystysilmoitusEpaonnistui
         :paasta-virhe-lapi? true})))

  KopioiToimenpiteetTaulukossaKaistoille
  (process-event [{:keys [rivi toimenpiteet-taulukko-atom sort-atom]} app]
    (let [kaistat (yllapitokohteet-domain/kaikki-kaistat rivi
                    (get-in app [:paallystysilmoitus-lomakedata
                                 :tr-osien-tiedot
                                 (:tr-numero rivi)]))
          rivi-ja-sen-kopiot (map #(assoc rivi :tr-kaista %) kaistat)
          kaikki-rivit (vals @toimenpiteet-taulukko-atom)
          rivit-idt-korjattuna (yllapitokohteet-domain/sailyta-idt-jos-sama-tr-osoite rivi-ja-sen-kopiot kaikki-rivit)
          avain-ja-rivi (fn [rivi]
                          {(select-keys rivi [:tr-numero :tr-ajorata :tr-kaista
                                              :tr-alkuosa :tr-alkuetaisyys
                                              :tr-loppuosa :tr-loppuetaisyys
                                              :toimenpide])
                           rivi})
          haettavat-rivit (map avain-ja-rivi (concat kaikki-rivit rivit-idt-korjattuna))
          rivit-ja-kopiot (->> haettavat-rivit
                            (into {})
                            vals
                            (jarjesta-rivit-fn-mukaan
                              (fn [rivi]
                                (jarjesta-valitulla-sort-funktiolla @sort-atom {:massat (:massat app)
                                                                                :murskeet (:murskeet app)
                                                                                :materiaalikoodistot (:materiaalikoodistot app)}
                                  rivi))))]
      (when toimenpiteet-taulukko-atom
        (reset! toimenpiteet-taulukko-atom rivit-ja-kopiot)
        (merkitse-muokattu app)))
    app)

  KopioiToimenpiteetTaulukossaAjoradoille
  (process-event [{:keys [rivi toimenpiteet-taulukko-atom sort-atom]} app]
    (let [rivi-ja-sen-kopiot (cond-> [rivi]
                               (#{1 2} (:tr-ajorata rivi))
                               (conj (-> rivi
                                       (update :tr-ajorata #(case %
                                                              1 2
                                                              2 1
                                                              %))
                                       (update :tr-kaista #(case %
                                                             11 21
                                                             12 22
                                                             21 11
                                                             22 12
                                                             %)))))
          kaikki-rivit (vals @toimenpiteet-taulukko-atom)
          rivit-idt-korjattuna (yllapitokohteet-domain/sailyta-idt-jos-sama-tr-osoite rivi-ja-sen-kopiot kaikki-rivit)
          avain-ja-rivi (fn [rivi]
                          {(select-keys rivi [:tr-numero :tr-ajorata :tr-kaista
                                              :tr-alkuosa :tr-alkuetaisyys
                                              :tr-loppuosa :tr-loppuetaisyys
                                              :toimenpide])
                           rivi})
          haettavat-rivit (map avain-ja-rivi (concat kaikki-rivit rivit-idt-korjattuna))
          rivit-ja-kopiot (->> haettavat-rivit
                            (into {})
                            vals
                            (jarjesta-rivit-fn-mukaan
                              (fn [rivi]
                                (jarjesta-valitulla-sort-funktiolla @sort-atom {:massat (:massat app)
                                                                                :murskeet (:murskeet app)
                                                                                :materiaalikoodistot (:materiaalikoodistot app)}
                                  rivi))))]
      (when toimenpiteet-taulukko-atom
        (reset! toimenpiteet-taulukko-atom rivit-ja-kopiot)
        (merkitse-muokattu app)))
    app)

  AvaaAlustalomake
  (process-event [{lomake :lomake} app]
    (let [lomake (if (empty? lomake)
                   {:tr-numero (get-in app [:paallystysilmoitus-lomakedata :perustiedot :tr-numero])}
                   lomake)]
      (assoc-in app [:paallystysilmoitus-lomakedata :alustalomake] lomake)))

  PaivitaAlustalomake
  (process-event [{alustalomake :alustalomake} app]
    ;; LJYR toimenpiteelle pitää laskea tie osoitteen pituus.
    ;; alustatoimenpiteet on mallinnettu niin, että niiden mallintamiskoodi on käytössä monessa paikassa
    ;; Ja pituuden laskentaa ei voi tehdä muualla, kuin tässä
    (do
      (when (= 42 (:toimenpide alustalomake))
         ;; Päivitettään LJYR toimenpiteelle pinta-ala tierekisteriosoitteen pituuden perusteella
        (let [{:keys [tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys]} alustalomake]
          ;; pituutta ei voi laskea, voi jokin arvoista puuttuu
          (when (and tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys)
            (tuck/action!
             (fn [e!]
               (e! (->LaskeTieosoitteenPituus {:tie tr-numero
                                               :aosa tr-alkuosa
                                               :aet tr-alkuetaisyys
                                               :losa tr-loppuosa
                                               :let tr-loppuetaisyys})))))))
      (assoc-in app [:paallystysilmoitus-lomakedata :alustalomake] alustalomake)))

  TallennaAlustalomake
  (process-event [{alustalomake :alustalomake
                   jatka? :jatka?} app]
    (let [idt (keys @alustarivit-atom)
          pienin-id (apply min idt)
          uusi-id (or (:muokkaus-grid-id alustalomake)
                    (if (or (nil? pienin-id)
                          (pos? pienin-id))
                      -1
                      (dec pienin-id)))
          alusta-params (lomakkeen-muokkaus/ilman-lomaketietoja alustalomake)
          ylimaaraiset-avaimet (pot2-domain/alusta-ylimaaraiset-lisaparams-avaimet alusta-params)
          alusta-params-ilman-ylimaaraisia (apply
                                             dissoc alusta-params ylimaaraiset-avaimet)
          uusi-rivi {uusi-id alusta-params-ilman-ylimaaraisia}
          rivit (jarjesta-rivit-fn-mukaan
                  (fn [rivi]
                    (jarjesta-valitulla-sort-funktiolla @valittu-alustan-sort {:massat (:massat app)
                                                                               :murskeet (:murskeet app)
                                                                               :materiaalikoodistot (:materiaalikoodistot app)}
                      rivi))
                  (vals (conj @alustarivit-atom uusi-rivi)))]
      (reset! alustarivit-atom rivit)
      (-> app
        (assoc-in [:paallystysilmoitus-lomakedata :alustalomake]
          (when jatka? (-> alusta-params
                         (assoc :tr-ajorata nil :tr-kaista nil
                           :tr-alkuosa nil :tr-alkuetaisyys nil :tr-loppuosa nil :tr-loppuetaisyys nil))))
        (assoc-in [:paallystysilmoitus-lomakedata :muokattu?] true))))

  SuljeAlustalomake
  (process-event [_ app]
    (assoc-in app [:paallystysilmoitus-lomakedata :alustalomake] nil))

  NaytaMateriaalilomake
  (process-event [{rivi :rivi sivulle? :sivulle?} app]
    (let [materiaali (rivi->massa-tai-murske rivi (select-keys app #{:massat :murskeet}))
          materiaali (if (nil? materiaali)
                       mk-tiedot/uusi-massa-map
                       materiaali)
          materiaali (if (::pot2-domain/massa-id materiaali)
                       (mk-tiedot/massa-kayttoliittyman-muotoon materiaali
                         (::pot2-domain/massa-id materiaali)
                         false)
                       materiaali)
          polku (if (:harja.domain.pot2/murske-id materiaali) :pot2-murske-lomake :pot2-massa-lomake)
          nil-polku (if (:harja.domain.pot2/murske-id materiaali) :pot2-massa-lomake :pot2-murske-lomake)]
      (-> app
        (assoc polku (merge materiaali
                       {:sivulle? sivulle?
                        :voi-muokata? false})
          nil-polku nil))))

  SuljeMateriaalilomake
  (process-event [_ app]
    (-> app
      (assoc :pot2-massa-lomake nil
        :pot2-murske-lomake nil)))

  Pot2Muokattu
  (process-event [_ app]
    (merkitse-muokattu app))

  KulutuskerrosMuokattu
  (process-event [{muokattu? :muokattu?} app]
    (if (nil? muokattu?)
      app
      (assoc-in app [:paallystysilmoitus-lomakedata :kulutuskerros-muokattu?] muokattu?)))

  LisaaPaallysterivi
  (process-event [{atomi :atomi} app]
    (lisaa-uusi-paallystekerrosrivi! atomi (get-in app [:paallystysilmoitus-lomakedata :perustiedot]))
    app)

  LaskeTieosoitteenPituus
  (process-event [{tie :tie} app]
    (do (tuck-apurit/post! :laske-tieosoitteen-pituus
          tie
          {:onnistui ->LaskeTieosoitteenPituusOnnistui
           :epaonnistui ->LaskeTieosoitteenPituusVirhe
           :paasta-virhe-lapi? true})
      app))

  LaskeTieosoitteenPituusOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [pinta-ala (* (get-in app [:paallystysilmoitus-lomakedata :alustalomake :leveys]) (:pituus vastaus)) #_ (fmt/desimaaliluku-opt
                      (* (get-in app [:paallystysilmoitus-lomakedata :alustalomake :leveys]) (:pituus vastaus))
                      1)]
      (assoc-in app [:paallystysilmoitus-lomakedata :alustalomake :pinta-ala] pinta-ala)))

  LaskeTieosoitteenPituusVirhe
  (process-event [{vastaus :vastaus} app]
    (js/console.error "Virhe  laskettaessa tieosoitteen pituutta:" (pr-str vastaus))
    app))
