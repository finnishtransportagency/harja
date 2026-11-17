(ns harja.ui.primitiivit.viesti)

(def flash-viestin-perusluokat
  {:overlay "harja-viesti-overlay"
   :tausta "harja-viesti-tausta"
   :runko "harja-viesti-runko"
   :sisalto "harja-viesti"})

(def flash-viestin-variantit
  {:success "harja-viesti-onnistuminen"
   :info "harja-viesti-info"
   :warning "harja-viesti-varoitus"
   :danger "harja-viesti-vaara"})

(defn flash-viestin-luokat
  "Palauttaa flash-viestin luokat. Tuntematon luokka putoaa info-varianttiin."
  [luokka]
  (assoc flash-viestin-perusluokat
    :variantti (get flash-viestin-variantit luokka "harja-viesti-info")))
