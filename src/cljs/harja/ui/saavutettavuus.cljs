(ns harja.ui.saavutettavuus
  "Saavutettavuuteen liittyviä komponentteja ja apufunktioita"
  (:require [reagent.core :as r])) 

(defonce aria-viestit (r/atom {:polite nil
                               :assertive nil}))

(defn aria-live-container
  "Komponentti, joka näyttää ruudunlukijoille tarkoitettuja ilmoituksia.  
   Komponentti on visuaalisesti piilotettu, mutta ruudunlukijat lukevat sen sisällön.  
     
   Parametrit:  
   - viesti: Näytettävä viesti (string tai reagent-komponentti)  
   - optiot: Map, joka voi sisältää seuraavat avaimet:  
     - :kohteliaisuus - 'polite' (oletus) tai 'assertive'  
     - :rooli - 'status' (oletus), 'alert', tai muu ARIA-rooli"
  ([] (aria-live-container nil {}))
  ([viesti] (aria-live-container viesti {}))
  ([viesti {:keys [kohteliaisuus rooli]
            :or {kohteliaisuus "polite"
                 rooli "status"}}]
   [:div {:role rooli
          :aria-live kohteliaisuus
          :style {:position "absolute"
                  :left "-9999px"
                  :height "1px"
                  :width "1px"
                  :overflow "hidden"}}
    viesti]))


;; Funktioita arvojen asettamiseen muista namespaceista
(defn aseta-aria-live-viesti!
  "Näyttää ilmoituksen ruudunlukijoille.  
     
   Parametrit:  
   - viesti: Näytettävä viesti  
   - kohteliaisuus: 'polite' (oletus) tai 'assertive'"
  ([viesti] (aseta-aria-live-viesti! viesti "polite"))
  ([viesti kohteliaisuus]
   (swap! aria-viestit assoc (keyword kohteliaisuus) viesti)
   ;; Tyhjennä viesti automaattisesti hetken kuluttua  
   (js/setTimeout #(swap! aria-viestit assoc (keyword kohteliaisuus) nil) 5000)))  