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
            [harja.tyokalut.functor :as functor])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce valinnat
         (reaction {:urakka-id (:id @nav/valittu-urakka)
                    :sopimus-id (first @urakka/valittu-sopimusnumero)
                    :hoitokausi @urakka/valittu-hoitokausi
                    :kuukausi @urakka/valittu-hoitokauden-kuukausi}))

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

(defn- tallenna-varustetoteuma [toteuma]
  (k/post! :tallenna-varustetoteuma toteuma))

(defn uusi-varuste
  "Luo uuden tyhjän varustetoteuman lomaketta varten."
  []
  {:toiminto :lisatty
   :tietolaji (ffirst varusteet/tietolaji->selitys)})

(extend-protocol t/Event
  v/YhdistaValinnat
  (process-event [{valinnat :valinnat} app]
    (hae (update app :valinnat merge valinnat)))

  v/HaeVarusteToteumat
  (process-event [_ {valinnat :valinnat :as app}]
    (let [tulos! (t/send-async! v/->VarusteToteumatHaettu)]
      (go
        (let [{:keys [urakka-id sopimus-id kuukausi hoitokausi tienumero]} valinnat]
          (tulos! (<! (hae-toteumat urakka-id sopimus-id
                                    (or kuukausi hoitokausi)
                                    tienumero)))))
      (assoc app
        :toteumahaku-id nil)))

  v/VarusteToteumatHaettu
  (process-event [{toteumat :toteumat} app]
    (assoc app
      :karttataso (varustetoteumat-karttataso toteumat)
      :karttataso-nakyvissa? true
      :toteumat toteumat))

  v/ValitseToteuma
  (process-event [{toteuma :toteuma} app]
    (assoc app
      :varustetoteuma toteuma))

  v/TyhjennaValittuToteuma
  (process-event [_ app]
    (assoc app :varustetoteuma nil))

  v/LisaaVaruste
  (process-event [_ _]
    (let [tulos! (t/send-async! (partial v/->AsetaToteumanTiedot (uusi-varuste)))]
      (tulos!)))

  v/AsetaToteumanTiedot
  (process-event [{tiedot :tiedot} {toteuma :varustetoteuma :as app}]
    (let [tietolaji-muuttui? (not= (:tietolaji tiedot) (:tietolaji toteuma))
          tiedot (if tietolaji-muuttui?
                   (assoc tiedot :tietolajin-kuvaus nil)
                   tiedot)
          uusi-toteuma (merge toteuma tiedot)
          koordinaattiarvot (or (get-in tiedot [:sijainti :coordinates])
                                (first (:points (first (:lines tiedot)))))
          ;; todo: pitää varmistaa millä tarkkuudella koordinaatit lähetetään
          koordinaatit (when koordinaattiarvot {:x (Math/round (first koordinaattiarvot))
                                                :y (Math/round (second koordinaattiarvot))})
          uusi-toteuma (assoc uusi-toteuma :arvot (merge (:arvot tiedot) koordinaatit))]
      ;; Jos tietolajin kuvaus muuttui ja se ei ole tyhjä, haetaan uudet tiedot
      ;; todo: vastauksen käsittelyyn (k/virhe)
      (when (and tietolaji-muuttui? (:tietolaji tiedot))
        (let [tulos! (t/send-async! (partial v/->TietolajinKuvaus (:tietolaji tiedot)))]
          (go
            (tulos! (<! (hae-tietolajin-kuvaus (:tietolaji tiedot)))))))

      (assoc app :varustetoteuma uusi-toteuma)))

  v/TietolajinKuvaus
  (process-event [{:keys [tietolaji kuvaus]} {toteuma :varustetoteuma :as app}]
    ;; Uusi tietolajin kuvaus haettu palvelimelta, aseta se paikoilleen, jos
    ;; toteuman tietolaji on sama kuin toteumassa.
    (if (= tietolaji (:tietolaji toteuma))
      (assoc-in app [:varustetoteuma :tietolajin-kuvaus] kuvaus)
      app))

  v/TallennaVarustetoteuma
  (process-event [_ {{:keys [arvot sijainti lisatieto tietolaji toiminto tierekisteriosoite] :as toteuma}
                     :varustetoteuma :as app}]
    ;; Tietolajin arvoista pitää purkaa koodiarvot ominaisuuden kuvauksen seasta
    (let [arvot (functor/fmap #(if (map? %) (:koodi %) %) arvot)
          toteuma {:arvot arvot
                   :sijainti sijainti
                   :tierekisteriosoite tierekisteriosoite
                   :lisatieto lisatieto
                   :tietolaji tietolaji
                   :toiminto toiminto
                   :urakka-id @nav/valittu-urakka-id}]
      (tallenna-varustetoteuma toteuma))))

(defonce karttataso-varustetoteuma (r/cursor varusteet [:karttataso-nakyvissa?]))
(defonce varusteet-kartalla (r/cursor varusteet [:karttataso]))
