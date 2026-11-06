const {app,BrowserWindow,Menu}=require('electron');
const path=require('path');
app.setName('Reinos de Eldmor');
function criar(){
  const w=new BrowserWindow({width:1280,height:800,minWidth:800,minHeight:500,backgroundColor:'#0d0b14',title:'Reinos de Eldmor',icon:path.join(__dirname,'game','icons','icon-512.png'),autoHideMenuBar:true,show:false,webPreferences:{contextIsolation:true,backgroundThrottling:false}});
  Menu.setApplicationMenu(null);
  w.once('ready-to-show',()=>w.show());
  w.webContents.on('before-input-event',(e,i)=>{if(i.type!=='keyDown')return;if(i.key==='F11'){w.setFullScreen(!w.isFullScreen());e.preventDefault()}if(i.key==='Escape'&&w.isFullScreen()&&i.alt)w.setFullScreen(false)});
  w.webContents.setWindowOpenHandler(()=>({action:'deny'}));
  w.loadFile(path.join(__dirname,'game','index.html'));
}
app.whenReady().then(criar);
app.on('window-all-closed',()=>app.quit());
