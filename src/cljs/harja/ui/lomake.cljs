(ns harja.ui.lomake
  "Lomakeapureita"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kentat :refer [tee-kentta atomina]]))

(defn lomake [{:keys [muokkaa! inline?] :as opts} kentat data]
  [:form {:class (when (:inline? opts) "form-inline")}
   (for [kentta kentat]
     ^{:key (:nimi kentta)}
     [:div.form-group
      [:label {:for (name (:nimi kentta))}
       (:otsikko kentta)]
      (if-let [komponentti (:komponentti kentta)]
        komponentti
        [tee-kentta (assoc kentta :lomake? true)
         (atomina kentta data muokkaa!)])])])
  
