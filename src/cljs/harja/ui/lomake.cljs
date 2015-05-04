(ns harja.ui.lomake
  "Lomakeapureita"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kentat :refer [tee-kentta atomina]]))

(defmulti kentan-otsikko (fn [luokka kentan-nimi teksti] luokka))

(defmethod kentan-otsikko :horizontal [_ kentan-nimi teksti]
  [:label.col-sm-2.control-label {:for kentan-nimi}
   teksti])

(defmethod kentan-otsikko :default [_ kentan-nimi teksti]
  [:label {:for kentan-nimi} teksti])

(defmulti kentan-komponentti (fn [luokka skeema komponentti] luokka))

(defmethod kentan-komponentti :horizontal [_ skeema komponentti]
  [:div {:class (str "col-sm-" (or (:leveys-col skeema) 10))}
   komponentti])

(defmethod kentan-komponentti :default [_ skeema komponentti]
  komponentti)

(defmulti lomake-footer (fn [luokka footer] luokka))

(defmethod lomake-footer :horizontal [_ footer]
  [:div.form-group
   [:div.col-sm-offset-2.col-sm-10
    footer]])

(defmethod lomake-footer :default [_ footer]
  footer)

(defn lomake [{:keys [muokkaa! luokka footer] :as opts} kentat data]
  (let [luokka (or luokka :default)]
    [:form {:class (case luokka
                     :inline "form-inline"
                     :horizontal "form-horizontal"
                     :default "")}
     (doall
      (for [{:keys [muokattava? fmt hae nimi] :as kentta} kentat]
        ^{:key (:nimi kentta)}
        [:div.form-group
         [kentan-otsikko luokka (name nimi) (:otsikko kentta)]

         [kentan-komponentti luokka kentta
          (if-let [komponentti (:komponentti kentta)]
            komponentti
            (if (or (nil? muokattava?)
                    (muokattava? data))
              ;; Muokattava tieto, tehdään sille kenttä
              [tee-kentta (assoc kentta :lomake? true)
               (atomina kentta data muokkaa!)]

              ;; Ei muokattava, näytetään
              [:div.form-control-static
               ((or fmt str) ((or hae #(get % nimi)) data))]))]]))
     
     (when footer
       [lomake-footer luokka footer])]))
  
