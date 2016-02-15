(ns harja.domain.paallystys.paallystys-ja-paikkaus-yhteiset
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