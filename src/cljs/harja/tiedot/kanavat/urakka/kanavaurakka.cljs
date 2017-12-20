(ns harja.tiedot.kanavat.urakka.kanavaurakka
  "Tässä namespacessa pidetään yllä koodia / arvoja, jota hyödynnetään yleisesti
   kaikissa kanavaurakoiden näkymissä."
  (:require [reagent.core :refer [atom]]
            [harja.id :refer [id-olemassa?]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavatoimenpide]
            [clojure.string :as str]
            [harja.domain.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(def kanavakohteet
  (reaction<!
    [urakka @nav/valittu-urakka]
    (when (and urakka
               (urakka/kanavaurakka? urakka))
      (k/post! :hae-urakan-kohteet {::urakka/id (:id urakka)}))))