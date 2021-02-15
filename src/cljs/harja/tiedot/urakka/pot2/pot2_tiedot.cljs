(ns harja.tiedot.urakka.pot2.pot2-tiedot
  (:require
    [reagent.core :refer [atom] :as r]
    [tuck.core :refer [process-event] :as tuck]
    [clojure.string :as str]
    [harja.domain.pot2 :as pot2-domain]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.loki :refer [log tarkkaile!]]
    [harja.ui.lomakkeen-muokkaus :as lomakkeen-muokkaus]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.domain.oikeudet :as oikeudet]
    [harja.ui.grid.gridin-muokkaus :as gridin-muokkaus]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defonce pot2-nakymassa? (atom false))
(defonce kohdeosat-atom (atom nil))
(defonce alustarivit-atom (atom nil))
(defonce lisatiedot-atom (atom nil))

(defrecord MuutaTila [polku arvo])
(defrecord PaivitaTila [polku f])
(defrecord HaePot2Tiedot [paallystyskohde-id])
(defrecord HaePot2TiedotOnnistui [vastaus])
(defrecord HaePot2TiedotEpaonnistui [vastaus])
(defrecord TallennaPot2Tiedot [])
(defrecord LisaaAlustaToimenpide [])
(defrecord ValitseAlustatoimenpide [toimenpide])
(defrecord PaivitaAlustalomake [alustalomake])
(defrecord TallennaAlustalomake [alustalomake jatka?])
(defrecord SuljeAlustalomake [])

(defn- paallystekerroksen-osat-jarjestysnrolla
  "Palauttaa päällystekerroksen osat järjestysnumerolla"
  [rivit jarjestysnro]
  )

(defn kulutuskerroksen-osat
  [rivit]
  (paallystekerroksen-osat-jarjestysnrolla rivit 1))

(defn alemman-paallystekerroksen-osat
  [rivit]
  (paallystekerroksen-osat-jarjestysnrolla rivit 2))

(defn onko-toimenpide-verkko? [alustatoimenpiteet koodi]
  (= koodi 667))

(defn- fmt-toimenpide-verkko [rivi materiaalikoodistot]
 [:span
   (str (pot2-domain/ainetyypin-koodi->nimi (:verkon-sijainnit materiaalikoodistot) (:verkon-sijainti rivi))
        ", " (pot2-domain/ainetyypin-koodi->nimi (:verkon-tarkoitukset materiaalikoodistot) (:verkon-tarkoitus rivi)))])

(defn- fmt-kentan-nimi  [kentta]
  (case kentta

    ;; jos on ääkkösiä, täytyy tehdä special case
    :lisatty-paksuus "Lisätty paksuus"

    :massamaara "Massa\u00ADmäärä"


    ;; ei korjattavaa
    (when kentta
      (str/replace (name kentta) #"-" " "))))

(defn toimenpiteen-tiedot
  [rivi]
  (let [toimenpide (:toimenpide rivi)]
    (fn [rivi]
      [:div.pot2-toimenpiteen-tiedot
       ;; Kaivetaan metatiedosta sopivat kentät. Tähän mahdollisimman geneerinen ratkaisu olisi hyvä
       (str/capitalize
         (str/join ", " (map #(when (:nimi %)
                                (str (fmt-kentan-nimi (:nimi %)) ": " ((:nimi %) rivi)))
                             (pot2-domain/alusta-toimenpidespesifit-metadata toimenpide))))])))

(defn materiaalin-tiedot [materiaali {:keys [materiaalikoodistot]}]
  [:div.pot2-materiaalin-tiedot
   (cond
     (some? (:murske-id materiaali))
     [mk-tiedot/murskeen-rikastettu-nimi (:mursketyypit materiaalikoodistot) materiaali :komponentti]

     (some? (:massa-id materiaali))
     [mk-tiedot/massan-rikastettu-nimi (:massatyypit materiaalikoodistot) materiaali :komponentti]

     :else nil)])


(extend-protocol tuck/Event

  MuutaTila
  (process-event [{:keys [polku arvo]} app]
    (assoc-in app polku arvo))
  PaivitaTila
  (process-event [{:keys [polku f]} app]
    (update-in app polku f))

  HaePot2Tiedot
  (process-event [{paallystyskohde-id :paallystyskohde-id} {urakka :urakka :as app}]
    (let [parametrit {:urakka-id (:id urakka)
                      :paallystyskohde-id paallystyskohde-id}]
      (tuck-apurit/post! app
                         :urakan-paallystysilmoitus-paallystyskohteella
                         parametrit
                         {:onnistui ->HaePot2TiedotOnnistui
                          :epaonnistui ->HaePot2TiedotEpaonnistui})))

  HaePot2TiedotOnnistui
  (process-event [{vastaus :vastaus} {urakka :urakka :as app}]
    (let [perustiedot (select-keys vastaus paallystys/perustiedot-avaimet)
          kulutuskerros (:paallystekerros vastaus)
          alusta (:alusta vastaus)
          lomakedata {:paallystyskohde-id (:paallystyskohde-id vastaus)
                      :perustiedot (merge perustiedot
                                          {:tr-osoite (select-keys perustiedot paallystys/tr-osoite-avaimet)})
                      :kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                                                  (:id urakka))
                      :paallystekerros kulutuskerros
                      :alusta alusta
                      :lisatiedot (:lisatiedot vastaus)}]
      (-> app
          (assoc :paallystysilmoitus-lomakedata lomakedata))))

  HaePot2TiedotEpaonnistui
  ;; fixme implement
  (process-event [{vastaus :vastaus} app]
    (println "HaePot2TiedotEpaonnistui " (pr-str vastaus))
    app)

  TallennaPot2Tiedot
  (process-event [_ {{urakka-id :id :as urakka} :urakka
                     {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila
                     paallystysilmoitus-lomakedata :paallystysilmoitus-lomakedata :as app}]
    (let [lahetettava-data (-> paallystysilmoitus-lomakedata
                               (select-keys #{:perustiedot :ilmoitustiedot :paallystyskohde-id})
                               (update :perustiedot lomakkeen-muokkaus/ilman-lomaketietoja)
                               (update-in [:perustiedot :asiatarkastus] lomakkeen-muokkaus/ilman-lomaketietoja)
                               (update-in [:perustiedot :tekninen-osa] lomakkeen-muokkaus/ilman-lomaketietoja)
                               (assoc :lisatiedot @lisatiedot-atom
                                      :versio 2)
                               (update :ilmoitustiedot dissoc :virheet)
                               (assoc :paallystekerros (gridin-muokkaus/filteroi-uudet-poistetut
                                                                (into (sorted-map)
                                                                      @kohdeosat-atom)))
                               (assoc :alusta (gridin-muokkaus/filteroi-uudet-poistetut
                                                (into (sorted-map)
                                                      @alustarivit-atom))))]
      (log "TallennaPot2Tiedot lahetettava-data: " (pr-str lahetettava-data))
      (tuck-apurit/post! app :tallenna-paallystysilmoitus
                         {:urakka-id urakka-id
                          :sopimus-id (first valittu-sopimusnumero)
                          :vuosi valittu-urakan-vuosi
                          :paallystysilmoitus lahetettava-data}
                         {:onnistui paallystys/->TallennaPaallystysilmoitusOnnistui
                          :epaonnistui paallystys/->TallennaPaallystysilmoitusEpaonnistui
                          :paasta-virhe-lapi? true})))

  LisaaAlustaToimenpide
  (process-event [_ app]
    (println "LisaaAlustaToimenpide " )
    (assoc-in app [:paallystysilmoitus-lomakedata :alustalomake] {}))

  ValitseAlustatoimenpide
  (process-event [{toimenpide :toimenpide} app]
    (println "ValitseAlustatoimenpide " (pr-str toimenpide))
    (assoc-in app [:paallystysilmoitus-lomakedata :alustalomake]
              {:toimenpide toimenpide}))

  PaivitaAlustalomake
  (process-event [{alustalomake :alustalomake} app]
    (println "PaivitaAlustalomake " (pr-str alustalomake))
    (assoc-in app [:paallystysilmoitus-lomakedata :alustalomake]
              alustalomake))

  TallennaAlustalomake
  (process-event [{alustalomake :alustalomake
                   jatka? :jatka?} app]
    (println "TallennaAlustalomake " (pr-str alustalomake jatka?))
    (let [idt (keys @alustarivit-atom)
          pienin-id (apply min idt)
          uusi-id (if (pos? pienin-id)
                    -1
                    (dec pienin-id))
          alusta-params (lomakkeen-muokkaus/ilman-lomaketietoja alustalomake)
          ylimaaraiset-avaimet (pot2-domain/alusta-ylimaaraiset-lisaparams-avaimet alusta-params)
          alusta-params-ilman-ylimaaraisia (apply
                                         dissoc alusta-params ylimaaraiset-avaimet)
          uusi-rivi {uusi-id alusta-params-ilman-ylimaaraisia}]
      (swap! alustarivit-atom conj uusi-rivi))
      (assoc-in app [:paallystysilmoitus-lomakedata :alustalomake] (when jatka? {})))

  SuljeAlustalomake
  (process-event [_ app]
    (println "SuljeAlustalomake ")
    (assoc-in app [:paallystysilmoitus-lomakedata :alustalomake] nil))

  )