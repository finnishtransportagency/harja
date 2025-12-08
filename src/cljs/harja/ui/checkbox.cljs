(ns harja.ui.checkbox
  (:require [harja.ui.yleiset :as y]
            [harja.ui.komponentti :as komp]))

(defn paivita-indeterminate-tila [input-id tila]
  (when-let [node (.getElementById js/document input-id)]
    (set! (.-indeterminate node)
      (= tila :osittain-valittu))))

(def checkbox-tila-keyword->boolean
  {:valittu true
   :ei-valittu false})

(def boolean->checkbox-tila-keyword
  {true :valittu
   false :ei-valittu})

(defn checkbox
  "Ottaa checkbox-tila atomin, joka määrittelee komponentin tilan.
  Tila-atomin mahdolliset arvot:
  :valittu, :ei-valittu, :osittain-valittu
  Ottaa myös nimen, joka ilmestyy checkboxin viereen. Voi olla nil, jos tekstiä
  ei haluta (tai teksti ei ole osa tätä komponenttia)
  Lisäksi ottaa mapin erilaisia optioita"
  ([tila-atom] (checkbox tila-atom nil {}))
  ([tila-atom nimi] (checkbox tila-atom nimi {}))
  ([tila-atom nimi optiot]
   (let [input-id (str "checkbox-" (gensym))
         label-id (str "label-" (gensym))]
     (komp/luo
       (komp/piirretty #(paivita-indeterminate-tila input-id @tila-atom))
       (komp/kun-muuttui #(paivita-indeterminate-tila input-id @tila-atom))
       (fn [tila-atom nimi {:keys [on-change width otsikon-luokka disabled?]}]
         (let [tila @tila-atom
               checked? (or (= tila :valittu)
                            (= tila :osittain-valittu))
               indeterminate? (= tila :osittain-valittu)
               vaihda-tila (fn [event]
                             (let [uusi-tila (case tila
                                               :valittu :ei-valittu
                                               :ei-valittu :valittu
                                               :osittain-valittu :ei-valittu)]
                               (reset! tila-atom uusi-tila)
                               (when on-change
                                 (on-change uusi-tila))
                               (.preventDefault event)
                               (.stopPropagation event)))]
           [:div {:style {:width (or width "100%")}}
            [:input.vayla-checkbox
             {:id input-id
              :type "checkbox"
              :checked checked?
              :disabled disabled?
              :on-click #(.stopPropagation %)
              :on-change vaihda-tila}]
            (when nimi
              [:label.checkbox-label
               {:id label-id
                :for input-id
                :on-click #(.stopPropagation %)
                :class (y/luokat
                         otsikon-luokka
                         (when disabled? "disabled")
                         (when indeterminate? "indeterminate"))}
               nimi])]))))))
