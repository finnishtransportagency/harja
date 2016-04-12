(ns harja.tiedot.urakka.toteumat.varusteet
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon kartalla-xf]]
            [harja.pvm :as pvm]
            [harja.geo :as geo])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn hae-toteumat [urakka-id sopimus-id [alkupvm loppupvm] tienumero]
  (k/post! :urakan-varustetoteumat
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :tienumero tienumero}))

(defonce tienumero (atom nil))

(def nakymassa? (atom false))

(def haetut-toteumat
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               sopimus-id (first @urakka/valittu-sopimusnumero)
               hoitokausi @urakka/valittu-hoitokausi
               kuukausi @urakka/valittu-hoitokauden-kuukausi
               tienumero @tienumero
               nakymassa? @nakymassa?]
              {:odota 500
               :nil-kun-haku-kaynnissa? true}
              (when nakymassa?
                (hae-toteumat urakka-id sopimus-id (or kuukausi hoitokausi) tienumero))))

(def varuste-toimenpide->string {nil         "Kaikki"
                                 :lisatty    "Lisätty"
                                 :paivitetty "Päivitetty"
                                 :poistettu  "Poistettu"
                                 :tarkastus  "Tarkastus"})

(def varustetoteumatyypit
  (vec varuste-toimenpide->string))

(def tietolaji->selitys
  {"tl523" "Tekninen piste"
   "tl501" "Kaiteet"
   "tl517" "Portaat"
   "tl507" "Bussipysäkin varusteet"
   "tl508" "Bussipysäkin katos"
   "tl506" "Liikennemerkki"
   "tl522" "Reunakivet"
   "tl513" "Reunapaalut"
   "tl196" "Bussipysäkit"
   "tl519" "Puomit ja kulkuaukot"
   "tl505" "Jätehuolto"
   "tl195" "Tienkäyttäjien palvelualueet"
   "tl504" "WC"
   "tl198" "Kohtaamispaikat ja levikkeet"
   "tl518" "Kivetyt alueet"
   "tl514" "Melurakenteet"
   "tl509" "Rummut"
   "tl515" "Aidat"
   "tl503" "Levähdysalueiden varusteet"
   "tl510" "Viheralueet"
   "tl512" "Viemärit"
   "tl165" "Välikaistat"
   "tl516" "Hiekkalaatikot"
   "tl511" "Viherkuviot"})

(def karttataso-varustetoteuma (atom false))

(defn- selite [{:keys [toimenpide tietolaji alkupvm]}]
  (str
   (pvm/pvm alkupvm) " "
   (varuste-toimenpide->string toimenpide)
   " "
   (tietolaji->selitys tietolaji)))

(def varusteet-kartalla
  (reaction
    (when karttataso-varustetoteuma
      (kartalla-esitettavaan-muotoon
       @haetut-toteumat
       nil nil
       (keep (fn [toteuma]
               (when-let [sijainti (some-> toteuma :reitti geo/pisteet first)]
                 (assoc toteuma
                        :tyyppi-kartalla :varustetoteuma
                        :selitys-kartalla (selite toteuma)
                        :sijainti {:type :point
                                   :coordinates sijainti}))))))))
