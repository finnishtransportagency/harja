(ns harja.tiedot.urakka.toteumat.varusteet
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
            [harja.domain.tierekisteri.varusteet :as varusteet]
            [harja.tyokalut.functor :as functor]
            [harja.tyokalut.vkm :as vkm])
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

                ;; Varustehaun hakuehdot ja tulokset
                :varustehaku {:hakuehdot {:haku-kaynnissa? false}

                              ;; Tällä hetkellä näytettävä tietolaji
                              ;; ja varusteet
                              :tietolaji nil
                              :varusteet nil}}))

(defn- hae [{valinnat :valinnat toteumahaku-id :toteumahaku-id :as app}]
  (when toteumahaku-id
    (.clearTimeout js/window toteumahaku-id))
  (assoc app
    :toteumahaku-id (.setTimeout js/window
                                 (t/send-async! v/->HaeVarusteToteumat)
                                 500)
    :toteumat nil))

(defn- selite [{:keys [toimenpide tietolaji alkupvm]}]
  (str
    (pvm/pvm alkupvm) " "
    (varusteet/varuste-toimenpide->string toimenpide)
    " "
    (varusteet/tietolaji->selitys tietolaji)))

(defn- varustetoteumat-karttataso [toteumat]
  (kartalla-esitettavaan-muotoon
    toteumat
    nil nil
    (keep (fn [toteuma]
            (when-let [sijainti (some-> toteuma :sijainti geo/pisteet first)]
              (assoc toteuma
                :tyyppi-kartalla :varustetoteuma
                :selitys-kartalla (selite toteuma)
                :sijainti {:type :point
                           :coordinates sijainti}))))))

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
                               {:keys [arvot
                                       sijainti
                                       puoli
                                       ajorata
                                       lisatieto
                                       tietolaji
                                       toiminto
                                       tierekisteriosoite
                                       alkupvm
                                       loppupvm] :as toteuma}]
  (let [arvot (functor/fmap #(if (map? %) (:koodi %) %) arvot)
        toteuma {:arvot arvot
                 :sijainti sijainti
                 :puoli puoli
                 :ajorata ajorata
                 :tierekisteriosoite tierekisteriosoite
                 :lisatieto lisatieto
                 :tietolaji tietolaji
                 :toiminto toiminto
                 :urakka-id @nav/valittu-urakka-id
                 :alkupvm alkupvm
                 :loppupvm loppupvm}
        hakuehdot {:urakka-id urakka-id
                   :sopimus-id sopimus-id
                   :alkupvm (first aikavali)
                   :loppupvm (second aikavali)
                   :tienumero tienumero}]
    (k/post! :tallenna-varustetoteuma {:hakuehdot hakuehdot :toteuma toteuma})))

(defn uusi-varustetoteuma
  "Luo uuden tyhjän varustetoteuman lomaketta varten."
  []
  {:toiminto :lisatty
   :tietolaji (ffirst varusteet/tietolaji->selitys)
   :alkupvm (pvm/nyt)
   :muokattava? true
   :ajoradat varusteet/oletus-ajoradat
   :ajorata (first varusteet/oletus-ajoradat)
   :puoli (first varusteet/tien-puolet)})

(defn naytettavat-toteumat [valittu-toimenpide toteumat]
  (if valittu-toimenpide
    (filter #(= valittu-toimenpide (:toimenpide %)) toteumat)
    toteumat))


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
  (process-event [{toteumat :toteumat} app]
    (let [valittu-toimenpide (:valittu-toimenpide app)]
      (assoc app
        :karttataso (varustetoteumat-karttataso toteumat)
        :karttataso-nakyvissa? true
        :toteumat toteumat
        :naytettavat-toteumat (naytettavat-toteumat valittu-toimenpide toteumat))))

  v/ValitseVarusteToteumanTyyppi
  (process-event [{tyyppi :tyyppi} {valinnat :valinnat toteumat :toteumat :as app}]
    (let [valittu-toimenpide (first tyyppi)
          naytettavat-toteumat (naytettavat-toteumat valittu-toimenpide toteumat)]
      (assoc app
        :karttataso (varustetoteumat-karttataso toteumat)
        :karttataso-nakyvissa? true
        :valinnat (assoc valinnat :tyyppi tyyppi)
        :naytettavat-toteumat naytettavat-toteumat)))

  v/ValitseToteuma
  (process-event [{toteuma :toteuma} _]
    (let [tulos! (t/send-async! (partial v/->AsetaToteumanTiedot (assoc toteuma :muokattava? false)))]
      (tulos!)))

  v/TyhjennaValittuToteuma
  (process-event [_ app]
    (assoc app :varustetoteuma nil))

  v/UusiVarusteToteuma
  (process-event [_ _]
    (let [tulos! (t/send-async! (partial v/->AsetaToteumanTiedot (uusi-varustetoteuma)))]
      (tulos!)))

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
        (let [valmis! (t/send-async! (partial v/->TietolajinKuvaus (:tietolaji tiedot)))]
          (go
            (let [vastaus (<! (hae-tietolajin-kuvaus (:tietolaji tiedot)))]
              (if (k/virhe? vastaus)
                (t/send-async! (partial v/->VirheTapahtui "Tietolajin hakemisessa tapahtui virhe"))
                (valmis! vastaus))))))

      (assoc app :varustetoteuma uusi-toteuma)))

  v/TietolajinKuvaus
  (process-event [{:keys [tietolaji kuvaus]} {toteuma :varustetoteuma :as app}]
    ;; Uusi tietolajin kuvaus haettu palvelimelta, aseta se paikoilleen, jos
    ;; toteuman tietolaji on sama kuin toteumassa.
    (if (= tietolaji (:tietolaji toteuma))
      (assoc-in app [:varustetoteuma :tietolajin-kuvaus] kuvaus)
      app))

  v/VarustetoteumaTallennettu
  (process-event [{toteumat :hakutulos} app]
    (let [toteumat (if toteumat toteumat [])]
      (assoc (dissoc app :varustetoteuma)
        :karttataso (varustetoteumat-karttataso toteumat)
        :karttataso-nakyvissa? true
        :toteumat toteumat
        :toteumahaku-id nil)))

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
    (log "---> toteumat:" (pr-str toteumat))

    (assoc app
      :karttataso (varustetoteumat-karttataso toteumat)
      :karttataso-nakyvissa? true
      :toteumat toteumat
      :naytettavat-toteumat toteumat #_(naytettavat-toteumat (get-in app [:valinnat :tyyppi]) toteumat))))

(defonce karttataso-varustetoteuma (r/cursor varusteet [:karttataso-nakyvissa?]))
(defonce varusteet-kartalla (r/cursor varusteet [:karttataso]))
