(ns harja.tiedot.urakka.toteumat.varusteet
  "Varustetoteumien tiedot ja niiden käsittely"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon kartalla-xf]]
            [harja.pvm :as pvm]
            [harja.geo :as geo]
            [tuck.core :as t]
            [harja.tiedot.urakka.toteumat.varusteet.viestit :as v]
            [harja.domain.tierekisteri.varusteet :as varusteet-domain]
            [harja.tyokalut.functor :as functor]
            [harja.tyokalut.tuck :as tuck-tyokalut]
            [harja.tyokalut.vkm :as vkm]
            [clojure.walk :as walk]
            [clojure.string :as str])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce valinnat
         (reaction {:urakka-id (:id @nav/valittu-urakka)
                    :sopimus-id (first @urakka/valittu-sopimusnumero)
                    :aikavali @urakka/valittu-aikavali
                    :tietolajit (map-indexed (fn [index tietolaji]
                                               {:id index
                                                :nimi tietolaji
                                                :valittu? true})
                                             (sort (vals varusteet-domain/tietolaji->selitys)))}))

(defonce varusteet
         (atom {:nakymassa? false

                :tienumero nil

                ;; Valinnat (urakka, sopimus, hk, kuukausi)
                :valinnat nil

                ;; Ajastetun toteumahaun id
                :toteumahaku-id nil

                ;; Toteumat, jotka on haettu nykyisten valintojen perusteella
                :toteumat nil

                ;; Karttataso varustetoteumille
                :karttataso-nakyvissa? false
                :karttataso nil

                ;; Valittu varustetoteuma
                :varustetoteuma nil

                ;; Voidaan antaa ulkopuolisesta siirtymästä valittu varustetoteuma
                ;; id, joka valitaan kun haku on valmistunut
                :valittu-toteumaid nil


                ;; Tierekisterin varusteiden hakuehdot ja tulokset
                :tierekisterin-varusteet {:hakuehdot {:haku-kaynnissa? false
                                                      :tietolaji (ffirst (vec varusteet-domain/tietolaji->selitys))
                                                      :varusteiden-haun-tila :sijainnilla
                                                      :voimassaolopvm (pvm/nyt)}
                                          ;; Tällä hetkellä näytettävä tietolaji ja varusteet
                                          :tietolaji nil
                                          :varusteet nil}}))

(defn valitse-toteuman-idlla! [toteumaid]
  (swap! varusteet assoc :valittu-toteumaid toteumaid))

(def ^:const ilmoitushaun-viive-ms 500)
(def ^:const taustahaun-viive-ms 20000)

(defn peru-taustahaku [{toteumahaku-id :toteumahaku-id}]
  (when toteumahaku-id
    (.clearTimeout js/window toteumahaku-id)))

(defn- hae [timeout {toteumahaku-id :toteumahaku-id :as app}]
  (peru-taustahaku app)
  (assoc app
    :toteumahaku-id (.setTimeout js/window
                                 (t/send-async! v/->HaeVarusteToteumat)
                                 timeout)))

(defn- tooltip [{:keys [toimenpide tietolaji alkupvm]}]
  (str
    (pvm/pvm alkupvm) " "
    (varusteet-domain/varuste-toimenpide->string toimenpide)
    " "
    (varusteet-domain/tietolaji->selitys tietolaji)))

(defn valittu-varustetoteuma-kartalla [varustetoteuma-kartalla valittu-varustetoteuma]
  (and valittu-varustetoteuma
       (or (and (:id varustetoteuma-kartalla)
                (= (:id valittu-varustetoteuma)
                   (:id varustetoteuma-kartalla)))
           (and (get-in valittu-varustetoteuma [:arvot :tunniste])
                (= (get-in valittu-varustetoteuma [:arvot :tunniste])
                   (get-in varustetoteuma-kartalla [:varuste :tunniste]))))))

(defn varustetoteumat-karttataso [toteumat tierekisterin-varusteet valittu-varustetoteuma]
  (kartalla-esitettavaan-muotoon
    (concat (keep (fn [toteuma]
                    (when-let [sijainti (some-> toteuma :sijainti geo/pisteet first)]
                      (assoc toteuma
                        :tyyppi-kartalla :varustetoteuma
                        :tooltip (tooltip toteuma)
                        :sijainti {:type :point
                                   :coordinates sijainti})))
                  toteumat)
            (map #(assoc % :tyyppi-kartalla :varuste) tierekisterin-varusteet))
    #(valittu-varustetoteuma-kartalla % valittu-varustetoteuma)))

(defn- hae-tietolajin-kuvaus [tietolaji]
  (k/post! :hae-tietolajin-kuvaus tietolaji))

(defn tallenna-varustetoteuma [{:keys [urakka-id
                                       sopimus-id
                                       aikavali
                                       tienumero] :as hakuehdot}
                               {:keys [id
                                       arvot
                                       sijainti
                                       puoli
                                       ajorata
                                       lisatieto
                                       tietolaji
                                       toiminto
                                       toimenpide
                                       tunniste
                                       tierekisteriosoite
                                       alkupvm
                                       loppupvm
                                       uusi-liite] :as toteuma}]
  (let [arvot (functor/fmap #(if (map? %) (:koodi %) %) arvot)
        toteuma {:id id
                 :arvot arvot
                 :sijainti sijainti
                 :ajorata ajorata
                 :tierekisteriosoite tierekisteriosoite
                 :lisatieto lisatieto
                 :tietolaji tietolaji
                 :toiminto (or toiminto toimenpide)
                 :urakka-id @nav/valittu-urakka-id
                 :kuntoluokitus (when (and (:kuntoluokitus arvot)
                                           (not (str/blank? (:kuntoluokitus arvot))))
                                  (js/parseInt (:kuntoluokitus arvot)))
                 :alkupvm alkupvm
                 :loppupvm loppupvm
                 :uusi-liite uusi-liite
                 :tunniste tunniste}

        toteuma (if (varusteet-domain/tien-puolellinen-tietolaji? tietolaji)
                  (assoc toteuma :puoli puoli)
                  toteuma)

        hakuehdot {:urakka-id urakka-id
                   :sopimus-id sopimus-id
                   :alkupvm (first aikavali)
                   :loppupvm (second aikavali)
                   :tienumero tienumero}]
    (k/post! :tallenna-varustetoteuma {:hakuehdot hakuehdot :toteuma toteuma})))

(defn varusteen-osoite [varuste]
  (when varuste
    (let [osoite (get-in varuste [:tietue :sijainti :tie])]
      {:numero (:numero osoite)
       :alkuosa (:aosa osoite)
       :alkuetaisyys (:aet osoite)
       :loppuosa (:losa osoite)
       :loppuetaisyys (:let osoite)})))

(defn uusi-varustetoteuma
  "Luo uuden tyhjän varustetoteuman lomaketta varten."
  ([toiminto] (uusi-varustetoteuma toiminto nil))
  ([toiminto {tietue :tietue :as varuste}]
   (let [tietolaji (or (get-in tietue [:tietolaji :tunniste]) (ffirst (varusteet-domain/muokattavat-tietolajit)))]
     {:toiminto toiminto
      :tietolaji tietolaji
      :alkupvm (or (:alkupvm tietue) (pvm/nyt))
      :muokattava? (not (= :nayta toiminto))
      :ajoradat varusteet-domain/oletus-ajoradat
      :ajorata (or (get-in tietue [:sijainti :tie :ajr]) (first varusteet-domain/oletus-ajoradat))
      :puoli (or (get-in tietue [:sijainti :tie :puoli]) (first (varusteet-domain/tien-puolet tietolaji)))
      :arvot (walk/keywordize-keys (get-in tietue [:tietolaji :arvot]))
      :tierekisteriosoite (varusteen-osoite varuste)
      :sijainti (:sijainti varuste)
      :tunniste (:tunniste varuste)})))

(defn- palauta-tilan-arvo [virheelliset-ainoastaan?]
  (if virheelliset-ainoastaan? "virhe" nil))

(defn naytettavat-toteumat [suodatukset toteumat]
  (reverse
    (sort-by :luotu
             (filter (fn [toteuma]
                       (every? true? (map #(let [k (first %)
                                                 v (last %)]
                                             (if (nil? v)
                                               true
                                               (= (get toteuma k) v)))
                                          suodatukset)))
                     toteumat))))

(defn hae-ajoradat [{vanha-tr :tierekisteriosoite}
                    {uusi-tr :tierekisteriosoite}
                    haku-valmis!
                    virhe!]
  (when (and uusi-tr
             (or (not (= (:numero uusi-tr) (:numero vanha-tr)))
                 (not (= (:alkuosa uusi-tr) (:alkuosa vanha-tr)))))
    (go (let [vastaus (<! (vkm/tieosan-ajoradat (:numero uusi-tr) (:alkuosa uusi-tr)))]
          (if (k/virhe? vastaus)
            (virhe!)
            (haku-valmis! vastaus))))))

(defn hae-liitteet
  [urakka-id toteuma-id haku-valmis! virhe!]
  (when toteuma-id
    (go (let [vastaus (<! (k/post! :hae-toteuman-liitteet {:urakka-id urakka-id
                                                           :toteuma-id toteuma-id
                                                           :oikeus (symbol "urakat-toteumat-varusteet")}))]
          (if (k/virhe? vastaus)
            (virhe!)
            (haku-valmis! vastaus))))))

(defn haetut-toteumat [app toteumat]
  (assoc app
    :toteumat toteumat
    :naytettavat-toteumat (naytettavat-toteumat {:tila (palauta-tilan-arvo (get-in app [:valinnat :virheelliset-ainoastaan?]))
                                                 :toimenpide (first (get-in app [:valinnat :tyyppi]))} toteumat)))

(defn kartalle [app]
  (assoc app :karttataso (varustetoteumat-karttataso (:naytettavat-toteumat app)
                                                     (get-in app [:tierekisterin-varusteet :varusteet])
                                                     (:varustetoteuma app))))

(defn hae-sijainnin-osoite [sijainti]
  (let [coords (.-coords sijainti)
        koordinaatit {:x (.-longitude coords)
                      :y (.-latitude coords)}]
    (k/post! :hae-tr-gps-koordinaateilla koordinaatit)))

(defn varustetoteuma-muokattava? [toteuma]
  (= "virhe" (:tila toteuma)))

(extend-protocol t/Event
  v/YhdistaValinnat
  (process-event [{valinnat :valinnat} app]
    (hae ilmoitushaun-viive-ms (update app :valinnat merge valinnat)))

  v/HaeVarusteToteumat
  (process-event [_ {valinnat :valinnat :as app}]
    (let [{:keys [urakka-id sopimus-id aikavali tienumero tietolajit]} valinnat]
      (-> app
          (tuck-tyokalut/post! :urakan-varustetoteumat
                               {:urakka-id urakka-id
                                :sopimus-id sopimus-id
                                :alkupvm (first aikavali)
                                :loppupvm (second aikavali)
                                :tienumero tienumero
                                :tietolajit (into #{}
                                                  (keep #(when (:valittu? %)
                                                           (varusteet-domain/selitys->tietolaji (:nimi %)))
                                                        tietolajit))}
                               {:onnistui v/->VarusteToteumatHaettu
                                       :epaonnistui (partial v/->VirheTapahtui "Varustetoteumien haussa tapahtui virhe")})
          (assoc :toteumahaku-id nil))))

  v/VarusteToteumatHaettu
  (process-event [{toteumat :toteumat}
                  {valittu-toimenpide :valittu-toimenpide
                   valittu-toteumaid :valittu-toteumaid
                   varustetoteuma :varustetoteuma
                   valinnat :valinnat
                   :as app}]
    (hae
      taustahaun-viive-ms
      (kartalle
        (assoc app
          :toteumat toteumat
          :naytettavat-toteumat (naytettavat-toteumat {:tila (palauta-tilan-arvo (:virheelliset-ainoastaan? valinnat))
                                                       :toimenpide valittu-toimenpide} toteumat)
          :varustetoteuma (if valittu-toteumaid
                            ;; Jos katsotaan vanhaa, päivitä tiedot palvelimelta
                            (some #(when (= (:toteumaid %) valittu-toteumaid) %)
                                  toteumat)

                            ;; Luomassa uutta, ei kosketa toteumaan
                            varustetoteuma)
          :valittu-toteumaid nil))))

  v/ValitseVarusteNaytetaanVirheelliset
  (process-event [{virheelliset-ainoastaan? :virheelliset-ainoastaan?} {:keys [valinnat toteumat] :as app}]
    (kartalle
      (assoc app
        :valinnat (assoc valinnat :virheelliset-ainoastaan? virheelliset-ainoastaan?)
        :naytettavat-toteumat (naytettavat-toteumat {:tila (palauta-tilan-arvo virheelliset-ainoastaan?)
                                                     :toimenpide (first (get-in app [:valinnat :tyyppi]))}
                                                    toteumat))))

  v/ValitseVarusteToteumanTyyppi
  (process-event [{tyyppi :tyyppi} {:keys [valinnat toteumat] :as app}]
    (let [valittu-toimenpide (first tyyppi)
          valittu-tila (palauta-tilan-arvo (:virheelliset-ainoastaan? valinnat))
          naytettavat-toteumat (naytettavat-toteumat {:tila valittu-tila
                                                      :toimenpide valittu-toimenpide} toteumat)]
      (kartalle
        (assoc app
          :valinnat (assoc valinnat :tyyppi tyyppi)
          :naytettavat-toteumat naytettavat-toteumat))))

  v/ValitseToteuma
  (process-event [{toteuma :toteuma} app]
    (peru-taustahaku app)
    (let [tulos! (t/send-async! (partial v/->AsetaToteumanTiedot (assoc toteuma :muokattava? (varustetoteuma-muokattava? toteuma))))]
      (kartalle (tulos!))))

  v/TyhjennaValittuToteuma
  (process-event [_ app]
    (kartalle (assoc app :varustetoteuma nil)))

  v/UusiVarusteToteuma
  (process-event [{:keys [toiminto varuste]} _]
    (let [tulos! (t/send-async! (partial v/->AsetaToteumanTiedot (uusi-varustetoteuma toiminto varuste)))]
      (kartalle (dissoc (tulos!) :muokattava-varuste :naytettava-varuste))))

  v/AsetaToteumanTiedot
  (process-event [{tiedot :tiedot} {nykyinen-toteuma :varustetoteuma :as app}]
    (let [nykyinen-tietolaji (:tietolaji tiedot)
          tietolaji-muuttui? (not= nykyinen-tietolaji (:tietolaji nykyinen-toteuma))
          tiedot (if tietolaji-muuttui?
                   (assoc tiedot :tietolajin-kuvaus nil)
                   tiedot)
          urakka-id (:urakkaid tiedot)
          toteuma-id (get-in tiedot [:toteuma :id])
          koordinaattiarvot (or (get-in tiedot [:sijainti :coordinates])
                                (first (:points (first (:lines tiedot)))))
          koordinaatit (when koordinaattiarvot {:x (Math/round (first koordinaattiarvot))
                                                :y (Math/round (second koordinaattiarvot))})
          arvot (if (and (not (nil? nykyinen-toteuma))
                         (or tietolaji-muuttui? (nil? (:arvot tiedot))))
                  (if (= nykyinen-tietolaji "tl506")
                    {:kuntoluokitus "5"
                     :lmkiinnit "1"
                     :lmmater "1"
                     :lmtyyppi "1"}
                    {})
                  (:arvot tiedot))
          uusi-toteuma (assoc (merge nykyinen-toteuma tiedot)
                              :arvot (merge arvot koordinaatit))]

      (hae-ajoradat nykyinen-toteuma
                    uusi-toteuma
                    (t/send-async! v/->TieosanAjoradatHaettu)
                    (t/send-async! (partial v/->VirheTapahtui "Ajoratojen haku epäonnistui")))

      (hae-liitteet urakka-id toteuma-id
                    (t/send-async! v/->LiitteetHaettu)
                    (t/send-async! (partial v/->VirheTapahtui "Liitteiden haku epäonnistui")))
      ;; Jos tietolajin kuvaus muuttui ja se ei ole tyhjä, haetaan uudet tiedot
      (when (and tietolaji-muuttui? (:tietolaji tiedot))
        (let [virhe! (t/send-async! (partial v/->VirheTapahtui "Tietolajin hakemisessa tapahtui virhe"))
              valmis! (t/send-async! (partial v/->TietolajinKuvaus (:tietolaji tiedot)))]
          (go
            (let [vastaus (<! (hae-tietolajin-kuvaus (:tietolaji tiedot)))]
              (if (k/virhe? vastaus)
                (virhe!)
                (valmis! vastaus))))))
      (kartalle
        (assoc app :varustetoteuma uusi-toteuma :muokattava-varuste nil :naytettava-varuste nil))))

  v/TietolajinKuvaus
  (process-event [{:keys [tietolaji kuvaus]} {toteuma :varustetoteuma :as app}]
    ;; Uusi tietolajin kuvaus haettu palvelimelta, aseta se paikoilleen, jos toteuman tietolaji on sama kuin toteumassa.
    (if (= tietolaji (:tietolaji toteuma))
      (assoc-in app [:varustetoteuma :tietolajin-kuvaus] kuvaus)
      app))

  v/VarustetoteumaTallennettu
  (process-event [{toteumat :hakutulos} app]
    (let [toteumat (if toteumat toteumat [])]
      (kartalle
        (-> app
            (assoc-in [:tierekisterin-varusteet :varusteet] nil)
            (dissoc :varustetoteuma :toteumahaku-id)
            (haetut-toteumat toteumat)))))

  v/TieosanAjoradatHaettu
  (process-event [{ajoradat :ajoradat} app]
    (let [nykyinen-ajorata (get-in app [:varustetoteuma :ajorata])
          ajorata (if (or (not nykyinen-ajorata) (not (some #(= nykyinen-ajorata %) ajoradat)))
                    (first ajoradat)
                    nykyinen-ajorata)]
      (-> app
          (assoc-in [:varustetoteuma :ajoradat] ajoradat)
          (assoc-in [:varustetoteuma :ajorata] ajorata))))

  v/LiitteetHaettu
  (process-event [{liitteet :liitteet} app]
    (-> app
        (assoc-in [:varustetoteuma :liitteet] liitteet)))

  v/VirheTapahtui
  (process-event [{virhe :virhe} app]
    (assoc app :virhe virhe))

  v/VirheKasitelty
  (process-event [_ app]
    (dissoc app :virhe))

  v/VarustetoteumatMuuttuneet
  (process-event [{varustetoteumat :varustetoteumat :as data} app]
    (kartalle (-> app
                  (dissoc :uudet-varustetoteumat)
                  (haetut-toteumat varustetoteumat))))

  v/LisaaLiitetiedosto
  (process-event [{liite :liite} app]
    (assoc-in app [:varustetoteuma :uusi-liite] liite))

  v/PoistaUusiLiitetiedosto
  (process-event [_ app]
    (assoc-in app [:varustetoteuma :uusi-liite] nil))

  v/PaivitaLiitteet
  (process-event [{liitteet :liitteet} app]
    (assoc-in app [:varustetoteuma :liitteet] liitteet))


  v/HaeSijainninOsoite
  (process-event [{sijainti :sijainti} app]
    (let [virhe! (t/send-async! (partial v/->VirheTapahtui "Osoitteen haku epäonnistui käyttäjän sijainnilla."))
          valmis! (t/send-async! v/->SijanninOsoiteHaettu)]
      (go
        (let [vastaus (<! (hae-sijainnin-osoite sijainti))]
          (if (k/virhe? vastaus)
            (virhe!)
            (valmis! vastaus))))
      app))

  v/SijanninOsoiteHaettu
  (process-event [{osoite :osoite} app]
    (let [tiedot (assoc (:varustetoteuma app)
                   :sijainti (:geometria osoite)
                   :tierekisteriosoite {:numero (:tie osoite)
                                        :alkuosa (:aosa osoite)
                                        :alkuetaisyys (:aet osoite)
                                        :loppuosa (:losa osoite)
                                        :loppuetaisyys (:let osoite)})]
      (-> ((t/send-async! (partial v/->AsetaToteumanTiedot tiedot)))))))

(defonce karttataso-varustetoteuma (r/cursor varusteet [:karttataso-nakyvissa?]))
(defonce varusteet-kartalla (r/cursor varusteet [:karttataso]))
