
-- Liitetään sanktiotyyppi sittenkin sanktiolajiin (voi olla useita)

ALTER TABLE sanktiotyyppi ADD COLUMN sanktiolaji sanktiolaji[];
