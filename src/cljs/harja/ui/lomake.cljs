(ns harja.ui.lomake
  "Lomakeapureita"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kentat :refer [tee-kentta atomina]]))

(defn lomake [{:keys [muokkaa! inline?] :as opts} kentat data]
  [:form {:class (when (:inline? opts) "form-inline")}
   (for [kentta kentat]
     [:div.form-group
      [:label {:for (name (:nimi kentta))}
       (:otsikko kentta)]
      [tee-kentta (assoc kentta :lomake? true)
       (atomina kentta data muokkaa!)]])])
  
