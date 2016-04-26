(ns harja.domain.paallystys-ja-paikkaus
  "Päällystys- ja paikkausurakoiden yhteisiä apureita")

(defn kuvaile-paatostyyppi [paatos]
  (case paatos
    :hyvaksytty "Hyväksytty"
    :hylatty "Hylätty"))

(defn nayta-tila [tila]
  (case tila
    :aloitettu "Aloitettu"
    :valmis "Valmis"
    :lukittu "Lukittu"
    "-"))

(defn nayta-paatos [tila]
  (case tila
    :hyvaksytty [:span.ilmoitus-hyvaksytty (kuvaile-paatostyyppi tila)]
    :hylatty [:span.ilmoitus-hylatty (kuvaile-paatostyyppi tila)]
    ""))