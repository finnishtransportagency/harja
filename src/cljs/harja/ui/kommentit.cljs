(ns harja.ui.kommentit
  "Kommentit-komponentti"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kentat :as kentat]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [clojure.string :as clj-str]))


(defn kommentit [{:keys [voi-kommentoida? kommentoi! uusi-kommentti placeholder
                         voi-liittaa? leveys-col liita-nappi-teksti
                         salli-poistaa-tallennettu-liite? poista-tallennettu-liite-fn
                         salli-poistaa-lisatty-liite?]} kommentit]
  [:div.kommentit
   (for [{:keys [aika tekijanimi kommentti tekija liite]} kommentit]
     ^{:key (pvm/millisekunteina aika)}
     [:div.kommentti {:class (when tekija (name tekija))}
      [:span.kommentin-tekija tekijanimi]
      [:span.kommentin-aika (pvm/pvm-aika aika)]
      [:div.kommentin-teksti kommentti]
      (when liite
        [liitteet/liitetiedosto liite
         {:salli-poisto? salli-poistaa-tallennettu-liite?
          :poista-liite-fn poista-tallennettu-liite-fn}])])
   (when voi-kommentoida?
     [:div.uusi-kommentti
      [:div.uusi-kommentti-teksti
       [kentat/tee-kentta {:tyyppi :text :nimi :teksti
                           :placeholder (or placeholder "Kirjoita uusi kommentti...")
                           :koko [(or leveys-col 80) :auto]}
        (r/wrap (:kommentti @uusi-kommentti) #(swap! uusi-kommentti assoc :kommentti %))]]
      (when kommentoi!
        [:button.nappi-ensisijainen.uusi-kommentti-tallenna
         {:on-click #(kommentoi! @uusi-kommentti)
          :disabled (clj-str/blank? (:kommentti @uusi-kommentti))}
         "Tallenna kommentti"])
      (when voi-liittaa? [liitteet/lisaa-liite
                          (:id @nav/valittu-urakka)
                          {:liite-ladattu #(swap! uusi-kommentti assoc :liite %)
                           :nappi-teksti (or liita-nappi-teksti "Lisää liite kommenttiin")
                           :salli-poistaa-lisatty-liite? salli-poistaa-lisatty-liite?
                           :poista-lisatty-liite-fn #(swap! uusi-kommentti dissoc :liite %)}])])])
