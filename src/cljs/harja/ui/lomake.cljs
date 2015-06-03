(ns harja.ui.lomake
  "Lomakeapureita"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.validointi :as validointi]
            [harja.ui.yleiset :refer [virheen-ohje]]
            [harja.ui.kentat :refer [tee-kentta atomina]]
            [harja.loki :refer [log logt tarkkaile!]]))

(defrecord Ryhma [otsikko skeemat])

(defn ryhma [otsikko & skeemat]
  (->Ryhma otsikko skeemat))

(defn ryhma? [x]
  (instance? Ryhma x))

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

(defn lomake [{:keys [muokkaa! luokka footer virheet] :as opts} skeema data]
  (let [luokka (or luokka :default)
        virheet (or virheet (atom {}))  ;; validointivirheet: (:id rivi) => [virheet]
        koskemattomat (atom (into #{} (map :nimi skeema)))]
    (tarkkaile! "koskemattomat" koskemattomat)
    (fn [{:keys [muokkaa! luokka footer] :as opts} skeema data]
      [:form.lomake {:class (case luokka
                      :inline "form-inline"
                      :horizontal "form-horizontal"
                      :default "")}
       (let [kentta (fn [{:keys [muokattava? fmt hae nimi] :as kentta}]
                      (assert (not (nil? nimi)) (str "Virheellinen kentän määrittely, :nimi arvo nil. Otsikko: " (:otsikko kentta)))
                      (let [kentan-virheet (get @virheet nimi)]
                        ^{:key (:nimi kentta)}
                        [:div.form-group
                         [kentan-otsikko luokka (name nimi) (:otsikko kentta)]
                         [kentan-komponentti luokka kentta
                          (if-let [komponentti (:komponentti kentta)]
                            komponentti
                            (if (or (nil? muokattava?)
                                    (muokattava? data))
                              ;; Muokattava tieto, tehdään sille kenttä
                              [:span {:class (str (when-not (empty? kentan-virheet)
                                                    "has-error"))}
                               [tee-kentta (assoc kentta :lomake? true)
                                (atomina kentta data (fn [uudet-tiedot]
                                                       (reset! virheet
                                                               (validointi/validoi-rivi nil uudet-tiedot skeema))
                                                       (swap! koskemattomat disj nimi)
                                                       (muokkaa! uudet-tiedot)))]
                               (when (and (not (empty? kentan-virheet))
                                          (not (@koskemattomat nimi)))
                                 (virheen-ohje kentan-virheet))]
                              
                              ;; Ei muokattava, näytetään
                              [:div.form-control-static
                               ((or fmt str) ((or hae #(get % nimi)) data))]))]]))]
         (doall
          (for [skeema (keep identity skeema)]
            (if-let [ryhma (and (ryhma? skeema) skeema)]
              ^{:key (:otsikko ryhma)}
              [:fieldset
               [:legend (:otsikko ryhma)]
               (doall (map kentta (keep identity (:skeemat ryhma))))]
              
              (kentta skeema)))))
       
       (when footer
         [lomake-footer luokka footer])])))

