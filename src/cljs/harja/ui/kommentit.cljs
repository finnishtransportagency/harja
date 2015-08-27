(ns harja.ui.kommentit
  "Kommentit-komponentti"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn kommentit [{:keys [voi-kommentoida? kommentoi! uusi-kommentti placeholder voi-liittaa leveys-col liita-nappi-teksti]} kommentit]
  [:div.kommentit
   (for [{:keys [aika tekijanimi kommentti tekija liite]} kommentit]
     ^{:key (pvm/millisekunteina aika)}
     [:div.kommentti {:class (when tekija (name tekija))}
      [:span.kommentin-tekija tekijanimi]
      [:span.kommentin-aika (pvm/pvm-aika aika)]
      [:div.kommentin-teksti kommentti]
      (when liite
        [liitteet/liitetiedosto liite])])
   (when voi-kommentoida?
     [:div.uusi-kommentti
      [:div.uusi-kommentti-teksti
       [kentat/tee-kentta {:tyyppi      :text :nimi :teksti
                           :placeholder (or placeholder "Kirjoita uusi kommentti...")
                           :koko        [(or leveys-col 80) :auto]}
        (r/wrap (:kommentti @uusi-kommentti) #(swap! uusi-kommentti assoc :kommentti %))]]
      (when kommentoi!
        [:button.nappi-ensisijainen.uusi-kommentti-tallenna
         {:on-click #(kommentoi! @uusi-kommentti)
          :disabled (str/blank? (:kommentti @uusi-kommentti))}
         "Tallenna kommentti"])
      (when voi-liittaa [liitteet/liite {:urakka-id     (:id @nav/valittu-urakka)
                                         :liite-ladattu #(swap! uusi-kommentti assoc :liite %)
                                         :nappi-teksti  liita-nappi-teksti}])])])