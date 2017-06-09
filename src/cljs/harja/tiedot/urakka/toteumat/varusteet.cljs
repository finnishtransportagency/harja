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
            [reagent.core :as r]
            [harja.domain.tierekisteri.varusteet :as varusteet-domain]
            [harja.tyokalut.functor :as functor]
            [harja.tyokalut.vkm :as vkm]
            [harja.domain.tierekisteri.varusteet :as tierekisteri-varusteet]
            [clojure.walk :as walk]
            [clojure.string :as str])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce valinnat
         (reaction {:urakka-id (:id @nav/valittu-urakka)
                    :sopimus-id (first @urakka/valittu-sopimusnumero)
                    :aikavali @urakka/valittu-aikavali}))

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
                                                      :tietolaji (ffirst (vec tierekisteri-varusteet/tietolaji->selitys))}
                                          ;; Tällä hetkellä näytettävä tietolaji ja varusteet
                                          :tietolaji nil
                                          :varusteet nil}}))

(defn valitse-toteuman-idlla! [toteumaid]
  (swap! varusteet assoc :valittu-toteumaid toteumaid))

(defn- hae [{valinnat :valinnat toteumahaku-id :toteumahaku-id :as app}]
  (when toteumahaku-id
    (.clearTimeout js/window toteumahaku-id))
  (assoc app
    :toteumahaku-id (.setTimeout js/window
                                 (t/send-async! v/->HaeVarusteToteumat)
                                 500)
    :toteumat nil))

(defn- tooltip [{:keys [toimenpide tietolaji alkupvm]}]
  (str
    (pvm/pvm alkupvm) " "
    (varusteet-domain/varuste-toimenpide->string toimenpide)
    " "
    (varusteet-domain/tietolaji->selitys tietolaji)))

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
    #(and valittu-varustetoteuma
          (or (and (:id %)
                   (= (:id valittu-varustetoteuma)
                      (:id %)))
              (and (get-in valittu-varustetoteuma [:arvot :tunniste])
                   (= (get-in valittu-varustetoteuma [:arvot :tunniste])
                      (get-in % [:varuste :tunniste])))))))

(defn- hae-toteumat [urakka-id sopimus-id [alkupvm loppupvm] tienumero]
  (k/post! :urakan-varustetoteumat
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm
            :tienumero tienumero}))

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
                                       tierekisteriosoite
                                       alkupvm
                                       loppupvm
                                       liitteet] :as toteuma}]
  (let [arvot (functor/fmap #(if (map? %) (:koodi %) %) arvot)
        toteuma {:id id
                 :arvot arvot
                 :sijainti sijainti
                 :puoli puoli
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
                 :liitteet liitteet}
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
   (let [tietolaji (or (get-in tietue [:tietolaji :tunniste]) (ffirst varusteet-domain/tietolaji->selitys))]
     {:toiminto toiminto
      :tietolaji tietolaji
      :alkupvm (or (:alkupvm tietue) (pvm/nyt))
      :muokattava? true
      :ajoradat varusteet-domain/oletus-ajoradat
      :ajorata (or (get-in tietue [:sijainti :tie :ajr]) (first varusteet-domain/oletus-ajoradat))
      :puoli (or (get-in tietue [:sijainti :tie :puoli]) (first (varusteet-domain/tien-puolet tietolaji)))
      :arvot (walk/keywordize-keys (get-in tietue [:tietolaji :arvot]))
      :tierekisteriosoite (varusteen-osoite varuste)})))

(defn naytettavat-toteumat [valittu-toimenpide toteumat]
  (reverse
    (sort-by :luotu
             (if valittu-toimenpide
               (filter #(= valittu-toimenpide (:toimenpide %)) toteumat)
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

(defn haetut-toteumat [app toteumat]
  (assoc app
    :toteumat toteumat
    :naytettavat-toteumat (naytettavat-toteumat (first (get-in app [:valinnat :tyyppi])) toteumat)))

(defn kartalle [app]
  (assoc app :karttataso (varustetoteumat-karttataso (:toteumat app)
                                                     (get-in app [:tierekisterin-varusteet :varusteet])
                                                     (:varustetoteuma app))))

(extend-protocol t/Event
  v/YhdistaValinnat
  (process-event [{valinnat :valinnat} app]
    (hae (update app :valinnat merge valinnat)))

  v/HaeVarusteToteumat
  (process-event [_ {valinnat :valinnat :as app}]
    (let [haku-valmis! (t/send-async! v/->VarusteToteumatHaettu)]
      (go
        (let [{:keys [urakka-id sopimus-id aikavali tienumero]} valinnat
              vastaus (<! (hae-toteumat urakka-id sopimus-id aikavali tienumero))]
          (if (k/virhe? vastaus)
            (t/send-async! (partial v/->VirheTapahtui "Varustetoteumien haussa tapahtui virhe"))
            (haku-valmis! vastaus))))
      (assoc app
        :toteumahaku-id nil)))

  v/VarusteToteumatHaettu
  (process-event [{toteumat :toteumat}
                  {valittu-toimenpide :valittu-toimenpide
                   valittu-toteumaid :valittu-toteumaid
                   :as app}]
    (kartalle
      (assoc app
        :toteumat toteumat
        :naytettavat-toteumat (naytettavat-toteumat valittu-toimenpide toteumat)
        :varustetoteuma (when valittu-toteumaid
                          (some #(when (= (:toteumaid %) valittu-toteumaid) %)
                                toteumat))
        :valittu-toteumaid nil)))

  v/ValitseVarusteToteumanTyyppi
  (process-event [{tyyppi :tyyppi} {valinnat :valinnat toteumat :toteumat :as app}]
    (let [valittu-toimenpide (first tyyppi)
          naytettavat-toteumat (naytettavat-toteumat valittu-toimenpide toteumat)]
      (kartalle
        (assoc app
          :valinnat (assoc valinnat :tyyppi tyyppi)
          :naytettavat-toteumat naytettavat-toteumat))))

  v/ValitseToteuma
  (process-event [{toteuma :toteuma} _]
    (let [tulos! (t/send-async! (partial v/->AsetaToteumanTiedot (assoc toteuma :muokattava? (= "virhe" (:tila toteuma)))))]
      (kartalle (tulos!))))

  v/TyhjennaValittuToteuma
  (process-event [_ app]
    (kartalle (assoc app :varustetoteuma nil)))

  v/UusiVarusteToteuma
  (process-event [{:keys [toiminto varuste]} _]
    (let [tulos! (t/send-async! (partial v/->AsetaToteumanTiedot (uusi-varustetoteuma toiminto varuste)))]
      (kartalle (dissoc (tulos!) :muokattava-varuste))))

  v/AsetaToteumanTiedot
  (process-event [{tiedot :tiedot} {nykyinen-toteuma :varustetoteuma :as app}]
    (let [tietolaji-muuttui? (not= (:tietolaji tiedot) (:tietolaji nykyinen-toteuma))
          tiedot (if tietolaji-muuttui?
                   (assoc tiedot :tietolajin-kuvaus nil)
                   tiedot)
          koordinaattiarvot (or (get-in tiedot [:sijainti :coordinates])
                                (first (:points (first (:lines tiedot)))))
          koordinaatit (when koordinaattiarvot {:x (Math/round (first koordinaattiarvot))
                                                :y (Math/round (second koordinaattiarvot))})
          uusi-toteuma (assoc (merge nykyinen-toteuma tiedot)
                         :arvot (merge (:arvot tiedot) koordinaatit))]

      (hae-ajoradat nykyinen-toteuma
                    uusi-toteuma
                    (t/send-async! v/->TieosanAjoradatHaettu)
                    (t/send-async! (partial v/->VirheTapahtui "Ajoratojen haku epäonnistui")))

      ;; Jos tietolajin kuvaus muuttui ja se ei ole tyhjä, haetaan uudet tiedot
      (when (and tietolaji-muuttui? (:tietolaji tiedot))
        (let [virhe! (t/send-async! (partial v/->VirheTapahtui "Tietolajin hakemisessa tapahtui virhe"))
              valmis! (t/send-async! (partial v/->TietolajinKuvaus (:tietolaji tiedot)))]
          (go
            (let [vastaus (<! (hae-tietolajin-kuvaus (:tietolaji tiedot)))]
              (if (k/virhe? vastaus)
                (virhe!)
                (valmis! vastaus))))))
      (assoc app :varustetoteuma uusi-toteuma :muokattava-varuste nil)))

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

  v/VirheTapahtui
  (process-event [{virhe :virhe} app]
    (assoc app :virhe virhe))

  v/VirheKasitelty
  (process-event [_ app]
    (dissoc app :virhe))

  v/VarustetoteumatMuuttuneet
  (process-event [{toteumat :varustetoteumat :as data} app]
    (kartalle (-> app
                  (dissoc :uudet-varustetoteumat)
                  (haetut-toteumat toteumat))))

  v/LisaaLiitetiedosto
  (process-event [{liite :liite} app]
    (assoc-in app [:varustetoteuma :liitteet] [liite])))

(defonce karttataso-varustetoteuma (r/cursor varusteet [:karttataso-nakyvissa?]))
(defonce varusteet-kartalla (r/cursor varusteet [:karttataso]))
