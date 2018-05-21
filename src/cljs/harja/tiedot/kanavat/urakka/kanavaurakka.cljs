(ns harja.tiedot.kanavat.urakka.kanavaurakka
  "Tässä namespacessa pidetään yllä koodia / arvoja, jota hyödynnetään yleisesti
   kaikissa kanavaurakoiden näkymissä."
  (:require [reagent.core :refer [atom] :as r]
            [harja.id :refer [id-olemassa?]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavatoimenpide]
            [clojure.string :as str]
            [harja.domain.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as kanavaurakan-toimenpiteet]
            [harja.asiakas.kommunikaatio :as k]

            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defonce valitun-kanavakohteen-id (atom nil))

(def kanavakohteet
  (reaction<!
    [urakka @nav/valittu-urakka]
    (when (and urakka
               (urakka/kanavaurakka? urakka))
      (k/post! :hae-urakan-kohteet {::urakka/id (:id urakka)}))))

;; TODO: kanavaurakan kohde
;; TODO: reaction-writable - huono pattern

(def kanavakohteet-mukaanlukien-poistetut
  (reaction<!
    [urakka @nav/valittu-urakka]
    (when (and urakka
               (urakka/kanavaurakka? urakka))
      (k/post! :hae-kaikki-urakan-kohteet (:id urakka)))))

(defonce valittu-kohde
         (reaction-writable
           (let [id @valitun-kanavakohteen-id ;; vai pitäikö olla @valittu-kohde-atom
                 kohteet @kanavakohteet-mukaanlukien-poistetut]
             (or (and id (first (filter #(= (:id %) id) kohteet)))
                 (first kohteet)))))

;; TODO: toimiiko, tarviiko muuta?
(defn valitse-kohde! [{id :id :as kohde}]
  (reset! valittu-kohde kohde))
